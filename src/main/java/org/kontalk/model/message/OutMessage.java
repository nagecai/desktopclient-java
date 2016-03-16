/*
 *  Kontalk Java client
 *  Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.model.message;

import org.kontalk.model.chat.Chat;
import org.kontalk.misc.JID;
import java.net.URI;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import org.jivesoftware.smack.util.StringUtils;
import org.kontalk.model.Contact;
import org.kontalk.system.Database;

/**
 * Model for an XMPP message from the user to a contact.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class OutMessage extends KonMessage {
    private static final Logger LOGGER = Logger.getLogger(OutMessage.class.getName());

    private final Set<Transmission> mTransmissions;

    public OutMessage(Database db, Chat chat, List<Contact> contacts,
            MessageContent content, boolean encrypted) {
        super(db, chat,
                "Kon_" + StringUtils.randomString(8),
                content,
                Optional.<Date>empty(),
                Status.PENDING,
                encrypted ?
                        CoderStatus.createToEncrypt() :
                        CoderStatus.createInsecure());

        Set<Transmission> ts = new HashSet<>();
        contacts.stream().forEach(contact -> {
            boolean succ = ts.add(new Transmission(db, contact, contact.getJID(), mID));
            if (!succ)
                LOGGER.warning("duplicate contact: "+contact);
        });
        mTransmissions = Collections.unmodifiableSet(ts);
    }

    // used when loading from database
    protected OutMessage(Database db, KonMessage.Builder builder) {
        super(db, builder);

        mTransmissions = Collections.unmodifiableSet(builder.mTransmissions);
    }

    public void setReceived(JID jid) {
        Transmission transmission = mTransmissions.stream()
                .filter(t -> t.getContact().getJID().equals(jid))
                .findFirst().orElse(null);
        if (transmission == null) {
            LOGGER.warning("can't find transmission for received status, IDs: "+jid);
            return;
        }

        if (transmission.isReceived())
            // probably by another client
            return;

        transmission.setReceived(new Date());
        this.changed(mStatus);
    }

    public void setStatus(Status status) {
        if (status == Status.IN || status == Status.RECEIVED) {
            LOGGER.warning("wrong status argument: "+status);
            return;
        }

        if (status == Status.SENT && mStatus != Status.PENDING)
            LOGGER.warning("unexpected new status of sent message: "+status);

        mStatus = status;
        if (status != Status.PENDING)
            mServerDate = new Date();
        this.save();
        this.changed(mStatus);
    }

    // Note: only one error per message (not transmission) possible
    public void setServerError(String condition, String text) {
        if (mStatus != Status.SENT)
            LOGGER.warning("unexpected status of message with error: "+mStatus);
        mServerError = new KonMessage.ServerError(condition, text);
        this.setStatus(Status.ERROR);
    }

    /** Update attachment after upload. */
    public void setUpload(URI url, String mime, long length) {
        MessageContent.Attachment attachment = this.getAttachment();
        if (attachment == null)
            return;

        attachment.update(url, mime, length);
        this.save();
    }

    @Override
    public Set<Transmission> getTransmissions() {
        return mTransmissions;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;

        // outmessages are only equal to outmessages
        if (!(o instanceof OutMessage))
            return false;

        return super.equals(o);
    }

    @Override
    public int hashCode() {
        int hash = 97 * super.hashCode();
        return hash;
    }
}
