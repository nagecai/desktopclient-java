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

package org.kontalk.view;

import com.alee.extended.panel.GroupPanel;
import com.alee.global.StyleConstants;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.rootpane.WebDialog;
import com.alee.laf.separator.WebSeparator;
import com.alee.laf.text.WebTextArea;
import com.alee.managers.notification.NotificationIcon;
import com.alee.managers.notification.NotificationListener;
import com.alee.managers.notification.NotificationManager;
import com.alee.managers.notification.NotificationOption;
import com.alee.managers.notification.WebNotificationPopup;
import com.alee.managers.popup.PopupStyle;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import javax.swing.Icon;
import org.kontalk.crypto.Coder;
import org.kontalk.crypto.PGPUtils;
import org.kontalk.misc.KonException;
import org.kontalk.model.Contact;
import org.kontalk.model.InMessage;
import org.kontalk.model.KonMessage;
import org.kontalk.util.MediaUtils;
import org.kontalk.util.Tr;
import static org.kontalk.view.View.GAP_DEFAULT;

/**
 * Inform user about events.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class Notifier {

    private static final Icon NOTIFICATION_ICON = Utils.getIcon("ic_msg_pending.png");

    private final View mView;

    Notifier(View view) {
        mView = view;
    }

    void onNewMessage(InMessage newMessage) {
        if (newMessage.getChat() == mView.getCurrentShownChat().orElse(null) &&
                mView.mainFrameIsFocused())
            return;
        MediaUtils.playSound(MediaUtils.Sound.NOTIFICATION);
    }

    void showException(KonException ex) {
        if (ex.getError() == KonException.Error.LOAD_KEY_DECRYPT) {
            mView.showPasswordDialog(true);
            return;
        }
        String errorText = Utils.getErrorText(ex);
        Icon icon = NotificationIcon.error.getIcon();
        NotificationManager.showNotification(errorText, icon);
    }

    // TODO more information for message exs
    void showSecurityErrors(KonMessage message) {
        String errorText = "<html>";

        boolean isOut = !message.isInMessage();
        errorText += isOut ? Tr.tr("Encryption error") : Tr.tr("Decryption error");
        errorText += ":";

        for (Coder.Error error : message.getCoderStatus().getErrors()) {
            errorText += "<br>";
            switch (error) {
                case UNKNOWN_ERROR:
                    errorText += Tr.tr("Unknown error");
                    break;
                case KEY_UNAVAILABLE:
                    errorText += Tr.tr("Key for receiver not found.");
                    break;
                case INVALID_PRIVATE_KEY:
                    errorText += Tr.tr("This message was encrypted with an old or invalid key");
                    break;
                default:
                    errorText += Tr.tr("Unusual coder error")+": " + error.toString();
            }
        }

        errorText += "</html>";

        // TODO too intrusive for user, but use the explanation above for message view
        //NotificationManager.showNotification(mChatView, errorText);
    }

    void confirmNewKey(final Contact contact, final PGPUtils.PGPCoderKey key) {
        WebPanel panel = new GroupPanel(GAP_DEFAULT, false);
        panel.setOpaque(false);

        panel.add(new WebLabel(Tr.tr("Received new key for contact")).setBoldFont());
        panel.add(new WebSeparator(true, true));

        panel.add(new WebLabel(Tr.tr("Contact:")));
        String contactText = Utils.name(contact) + " " + Utils.jid(contact, 30, true);
        panel.add(new WebLabel(contactText).setBoldFont());

        panel.add(new WebLabel(Tr.tr("Key fingerprint:")));
        WebTextArea fpArea = Utils.createFingerprintArea();
        fpArea.setText(Utils.fingerprint(key.fingerprint));
        panel.add(fpArea);

        String expl = Tr.tr("When declining the key further communication to and from this contact will be blocked.");
        WebTextArea explArea = new WebTextArea(expl, 3, 30);
        explArea.setEditable(false);
        explArea.setLineWrap(true);
        explArea.setWrapStyleWord(true);
        panel.add(explArea);

        WebNotificationPopup popup = NotificationManager.showNotification(panel,
                NotificationOption.accept, NotificationOption.decline,
                NotificationOption.cancel);
        popup.setClickToClose(false);
        popup.addNotificationListener(new NotificationListener() {
            @Override
            public void optionSelected(NotificationOption option) {
                switch (option) {
                    case accept :
                        mView.getControl().acceptKey(contact, key);
                        break;
                    case decline :
                        mView.getControl().declineKey(contact);
                }
            }
            @Override
            public void accepted() {}
            @Override
            public void closed() {}
        });
    }

    void confirmContactDeletion(final Contact contact) {
        WebPanel panel = new GroupPanel(GAP_DEFAULT, false);
        panel.setOpaque(false);

        panel.add(new WebLabel(Tr.tr("Contact was deleted on server")).setBoldFont());
        panel.add(new WebSeparator(true, true));

        String contactText = Utils.name(contact) + " " + Utils.jid(contact, 30, true);
        panel.add(new WebLabel(contactText).setBoldFont());

        String expl = Tr.tr("Remove this contact from your contact list?") + "\n" +
                View.REMOVE_CONTACT_NOTE;
        WebTextArea explArea = new WebTextArea(expl, 3, 30);
        explArea.setEditable(false);
        explArea.setLineWrap(true);
        explArea.setWrapStyleWord(true);
        panel.add(explArea);

        WebNotificationPopup popup = NotificationManager.showNotification(panel,
                NotificationOption.yes, NotificationOption.no,
                NotificationOption.cancel);
        popup.setClickToClose(false);
        popup.addNotificationListener(new NotificationListener() {
            @Override
            public void optionSelected(NotificationOption option) {
                switch (option) {
                    case yes :
                        mView.getControl().deleteContact(contact);
                }
            }
            @Override
            public void accepted() {}
            @Override
            public void closed() {}
        });
    }

    // TODO not used
    private void showNotification() {
        final WebDialog dialog = new WebDialog();
        dialog.setUndecorated(true);
        dialog.setBackground(Color.BLACK);
        dialog.setBackground(StyleConstants.transparent);

        WebNotificationPopup popup = new WebNotificationPopup(PopupStyle.dark);
        popup.setIcon(Utils.getIcon("kontalk_small.png"));
        popup.setMargin(View.MARGIN_DEFAULT);
        popup.setDisplayTime(6000);
        popup.addNotificationListener(new NotificationListener() {
            @Override
            public void optionSelected(NotificationOption option) {
            }
            @Override
            public void accepted() {
            }
            @Override
            public void closed() {
                dialog.dispose();
            }
        });

        // content
        WebPanel panel = new WebPanel();
        panel.setMargin(View.MARGIN_DEFAULT);
        panel.setOpaque(false);
        WebLabel title = new WebLabel("A new Message!");
        title.setFontSize(14);
        title.setForeground(Color.WHITE);
        panel.add(title, BorderLayout.NORTH);
        String text = "this is some message, and some longer text was added";
        WebLabel message = new WebLabel(text);
        message.setForeground(Color.WHITE);
        panel.add(message, BorderLayout.CENTER);
        popup.setContent(panel);

        //popup.packPopup();
        dialog.setSize(popup.getPreferredSize());

        // set position on screen
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gd = ge.getDefaultScreenDevice();
        GraphicsConfiguration gc = gd.getDefaultConfiguration();
        Rectangle screenBounds = gc.getBounds();
        // get height of the task bar
        // doesn't work on all environments
        //Insets toolHeight = toolkit.getScreenInsets(popup.getGraphicsConfiguration());
        int toolHeight  = 40;
        dialog.setLocation(screenBounds.width - dialog.getWidth() - 10,
                screenBounds.height - toolHeight - dialog.getHeight());

        dialog.setVisible(true);
        NotificationManager.showNotification(dialog, popup);
    }
}
