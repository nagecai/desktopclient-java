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

import com.alee.extended.label.WebLinkLabel;
import com.alee.extended.panel.GroupPanel;
import com.alee.laf.label.WebLabel;
import com.alee.laf.list.UnselectableListModel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.scroll.WebScrollPane;
import com.alee.laf.text.WebTextArea;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.Vector;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JViewport;
import javax.swing.ScrollPaneConstants;
import org.kontalk.system.Downloader;
import org.kontalk.crypto.Coder;
import org.kontalk.model.KonMessage;
import org.kontalk.model.KonThread;
import org.kontalk.model.MessageContent.Attachment;

/**
 * Pane that shows the currently selected thread.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
final class ThreadView extends WebScrollPane {
    private final static Logger LOGGER = Logger.getLogger(ThreadView.class.getName());

    private final static Icon PENDING_ICON = View.getIcon("ic_msg_pending.png");;
    private final static Icon SENT_ICON = View.getIcon("ic_msg_sent.png");
    private final static Icon DELIVERED_ICON = View.getIcon("ic_msg_delivered.png");
    private final static Icon ERROR_ICON = View.getIcon("ic_msg_error.png");
    private final static Icon CRYPT_ICON = View.getIcon("ic_msg_crypt.png");
    private final static Icon UNENCRYPT_ICON = View.getIcon("ic_msg_unencrypt.png");
    private final static Image BG_IMAGE = View.getImage("thread_bg.png");

    private final Map<Integer, MessageViewList> mThreadCache = new HashMap<>();
    private int mCurrentThreadID = -1;

    ThreadView() {
        super(null);

        this.setHorizontalScrollBarPolicy(
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        this.getVerticalScrollBar().setUnitIncrement(25);

        this.setViewport(new JViewport() {
            @Override
            public void paintComponent(Graphics g) {
                super.paintComponent(g);
                // tiling
                int iw = BG_IMAGE.getWidth(this);
                int ih = BG_IMAGE.getHeight(this);
                if (iw > 0 && ih > 0) {
                    for (int x = 0; x < getWidth(); x += iw) {
                        for (int y = 0; y < getHeight(); y += ih) {
                            g.drawImage(BG_IMAGE, x, y, iw, ih, this);
                        }
                    }
                }
            }
        });
    }

    int getCurrentThreadID() {
        return mCurrentThreadID;
    }

    void showThread(KonThread thread) {
        boolean isNew = false;
        if (!mThreadCache.containsKey(thread.getID())) {
            mThreadCache.put(thread.getID(), new MessageViewList(thread));
            isNew = true;
        }
        MessageViewList table = mThreadCache.get(thread.getID());
        this.setViewportView(table);

        if (table.getRowCount() > 0 && isNew) {
            // scroll down
            // TODO does not work right (again), probably bcause of row height
            // adjustment
            table.scrollToRow(table.getRowCount()-1);
        }

        mCurrentThreadID = thread.getID();
    }

    void setColor(Color color) {
        this.getViewport().setBackground(color);
    }

    private void removeThread(int id) {
        mThreadCache.remove(id);
        if(mCurrentThreadID == id) {
            mCurrentThreadID = -1;
            this.setViewportView(null);
        }
    }

    /**
     * View all messages of one thread in a left/right MIM style list.
     */
    private class MessageViewList extends TableView implements Observer {

        private final KonThread mThread;

        MessageViewList(KonThread thread) {
            super();

            mThread = thread;

            //this.setEditable(false);
            //this.setAutoscrolls(true);
            this.setOpaque(false);

            this.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    // this table was resized, the size of each item might have
                    // changed and each row height must be adjusted
                    // TODO efficient?
                    MessageViewList table = MessageViewList.this;
                    for (int row = 0; row < table.getRowCount(); row++) {
                        MessageViewList.this.setHeight(row);
                    }
                }
            });

            // disable selection
            this.setSelectionModel(new UnselectableListModel());

            // insert messages
            for (KonMessage message: mThread.getMessages()) {
                this.addMessage(message);
            }

            mThread.addObserver(this);
        }

        @Override
        public void update(Observable o, Object arg) {
            if (mThread.isDeleted()) {
                ThreadView.this.removeThread(mThread.getID());
            }

            // check for new messages to add
            if (mTableModel.getRowCount() < mThread.getMessages().size()) {
                Set<KonMessage> oldMessages = new HashSet<>();
                for (Object vec : mTableModel.getDataVector()) {
                    TableItem m = (TableItem) ((Vector) vec).elementAt(0);
                    oldMessages.add(((MessageView) m).mMessage);
                }

                for (KonMessage message: mThread.getMessages()) {
                    if (!oldMessages.contains(message)) {
                        // always inserted at the end, timestamp of message is
                        // ignored. Let's call it a feature.
                        this.addMessage(message);
                        // scroll to new message
                        this.scrollToRow(this.getRowCount()-1);
                    }
                }
            }

            if (ThreadView.this.mCurrentThreadID == mThread.getID()) {
                // we are seeing this thread right now, avoid loop
                if (!mThread.isRead())
                    mThread.setRead();
            }
        }

        private void addMessage(KonMessage message) {
            MessageView newMessageView = new MessageView(message);
            Object[] data = {newMessageView};
            mTableModel.addRow(data);

            this.setHeight(this.getRowCount() -1);
        }

        /**
         * Row height must be adjusted manually to component height.
         * source: https://stackoverflow.com/a/1784601
         * @param row the row that gets set
         */
        private void setHeight(int row) {
            Component comp = this.prepareRenderer(this.getCellRenderer(row, 0), row, 0);
            int height = Math.max(this.getRowHeight(), comp.getPreferredSize().height);
            this.setRowHeight(row, height);
        }

        /**
         * View for one message.
         * The content is added to a panel inside this panel.
         */
        private class MessageView extends TableItem implements Observer {

            private final KonMessage mMessage;
            private final WebPanel mContentPanel;
            private final WebTextArea mTextArea;
            private final int mPreferredTextAreaWidth;
            private final WebLabel mStatusIconLabel;

            MessageView(KonMessage message) {
                mMessage = message;

                this.setOpaque(false);
                this.setMargin(2);
                //this.setBorder(new EmptyBorder(10, 10, 10, 10));

                WebPanel messagePanel = new WebPanel(true);
                messagePanel.setWebColoredBackground(false);
                messagePanel.setMargin(5);
                if (mMessage.getDir().equals(KonMessage.Direction.IN))
                    messagePanel.setBackground(Color.WHITE);
                else
                    messagePanel.setBackground(View.LIGHT_BLUE);

                // from label
                if (mMessage.getDir().equals(KonMessage.Direction.IN)) {
                    String from;
                    if (!mMessage.getUser().getName().isEmpty()) {
                        from = mMessage.getUser().getName();
                    } else {
                        from = mMessage.getJID();
                        if (from.length() > 40)
                            from = from.substring(0, 8) + "...";
                    }
                    WebLabel fromLabel = new WebLabel(" "+from);
                    fromLabel.setFontSize(12);
                    fromLabel.setForeground(Color.BLUE);
                    fromLabel.setItalicFont();
                    messagePanel.add(fromLabel, BorderLayout.NORTH);
                }

                mContentPanel = new WebPanel();
                mContentPanel.setOpaque(false);
                // text
                boolean encrypted = mMessage.getCoderStatus().getEncryption() ==
                        Coder.Encryption.ENCRYPTED;
                // TODO display all possible content
                String text = encrypted ? "[encrypted]" : mMessage.getContent().getText();
                mTextArea = new WebTextArea(text);
                // hide area if there is no text
                mTextArea.setVisible(!text.isEmpty());
                mTextArea.setOpaque(false);
                mTextArea.setFontSize(13);
                mTextArea.setFontStyle(false, encrypted);
                // save the width that is requied to show the text in one line
                mPreferredTextAreaWidth = mTextArea.getPreferredSize().width;
                mTextArea.setLineWrap(true);
                mTextArea.setWrapStyleWord(true);
                mContentPanel.add(mTextArea, BorderLayout.CENTER);
                messagePanel.add(mContentPanel, BorderLayout.CENTER);

                WebPanel statusPanel = new WebPanel();
                statusPanel.setOpaque(false);
                statusPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
                // icons
                mStatusIconLabel = new WebLabel();

                this.update();

                statusPanel.add(mStatusIconLabel);
                WebLabel encryptIconLabel = new WebLabel();
                if (message.getCoderStatus().isEncrypted()) {
                    encryptIconLabel.setIcon(CRYPT_ICON);
                } else {
                    encryptIconLabel.setIcon(UNENCRYPT_ICON);
                }
                statusPanel.add(encryptIconLabel);
                // date label
                SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, HH:mm");
                WebLabel dateLabel = new WebLabel(dateFormat.format(mMessage.getDate()));
                dateLabel.setForeground(Color.GRAY);
                dateLabel.setFontSize(11);
                statusPanel.add(dateLabel);
                messagePanel.add(statusPanel, BorderLayout.SOUTH);

                if (mMessage.getDir().equals(KonMessage.Direction.IN)) {
                    this.add(messagePanel, BorderLayout.WEST);
                } else {
                    this.add(messagePanel, BorderLayout.EAST);
                }

                mMessage.addObserver(this);
            }

            public int getMessageID() {
                return mMessage.getID();
            }

            @Override
            void resize(int listWidth) {
                // note: on the very first call the list width is zero
                int maxWidth = (int)(listWidth * 0.8);
                int width = Math.min(mPreferredTextAreaWidth, maxWidth);
                // height is reset later
                mTextArea.setSize(width, mTextArea.getPreferredSize().height);
            }

            /**
             * Update what can change in a message: icon and attachment.
             */
            private void update() {
                // status icon
                if (mMessage.getDir() == KonMessage.Direction.OUT) {
                    switch (mMessage.getReceiptStatus()) {
                        case PENDING :
                            mStatusIconLabel.setIcon(PENDING_ICON);
                            break;
                        case SENT :
                            mStatusIconLabel.setIcon(SENT_ICON);
                            break;
                        case RECEIVED:
                            mStatusIconLabel.setIcon(DELIVERED_ICON);
                            break;
                        case ERROR:
                            mStatusIconLabel.setIcon(ERROR_ICON);
                            break;
                        default:
                            LOGGER.warning("unknown message receipt status!?");
                    }
                }

                // attachment
                Optional<Attachment> optAttachment = mMessage.getContent().getAttachment();
                if (optAttachment.isPresent()) {
                    String base = Downloader.getInstance().getAttachmentDir();
                    String fName = optAttachment.get().getFileName();
                    Path path = Paths.get(base, fName);

                    // rely on mime type in message
                    if (!optAttachment.get().getFileName().isEmpty() &&
                            optAttachment.get().getMimeType().startsWith("image")) {
                        // file should be present and should be an image, show it
                        BufferedImage image = readImage(path.toString());
                        double scale = Math.min(
                                300 /(image.getWidth() * 1.0),
                                200 /(image.getHeight() * 1.0));
                        scale = Math.min(1, scale);
                        Image scaledImage = image.getScaledInstance(
                                (int) (image.getWidth() * scale),
                                (int) (image.getHeight() * scale),
                                Image.SCALE_FAST);
                        WebLinkLabel imageView = new WebLinkLabel();
                        // TODO will this work on Windows?
                        imageView.setLink("", "file://"+path.toString());
                        imageView.setIcon(new ImageIcon(scaledImage));
                        mContentPanel.add(imageView, BorderLayout.SOUTH);
                    } else {
                        // show a link to the file
                        WebLabel attLabel;
                        if (optAttachment.get().getFileName().isEmpty()) {
                            attLabel = new WebLabel("?");
                        } else {
                            WebLinkLabel linkLabel = new WebLinkLabel();
                            // TODO will this work on Windows?
                            linkLabel.setLink(fName, "file://"+path.toString());
                            attLabel = linkLabel;
                        }
                        WebLabel labelLabel = new WebLabel("Attachment: ");
                        labelLabel.setItalicFont();
                        GroupPanel attachmentPanel = new GroupPanel(4, true, labelLabel, attLabel);
                        mContentPanel.add(attachmentPanel, BorderLayout.SOUTH);
                    }
                }
            }

            @Override
            public void update(Observable o, Object arg) {
                this.update();
                // need to repaint parent to see changes
                ThreadView.this.repaint();
            }

            @Override
            public String getTooltipText() {
                String encryption = "unknown";
                switch (mMessage.getCoderStatus().getEncryption()) {
                    case NOT: encryption = "not encrypted"; break;
                    case ENCRYPTED: encryption = "encrypted"; break;
                    case DECRYPTED: encryption = "decrypted"; break;
                }
                String verification = "unknown";
                switch (mMessage.getCoderStatus().getSigning()) {
                    case NOT: verification = "not signed"; break;
                    case SIGNED: verification = "signed"; break;
                    case VERIFIED: verification = "verified"; break;
                }
                String problems = "";
                if (mMessage.getCoderStatus().getErrors().isEmpty()) {
                    problems = "none";
                } else {
                  for (Coder.Error error: mMessage.getCoderStatus().getErrors()) {
                      problems += error.toString() + " <br> ";
                  }
                }

                String html = "<html><body>" +
                        //"<h3>Header</h3>" +
                        "<br>" +
                        "Security: " + encryption + " / " + verification + "<br>" +
                        "Problems: " + problems;

                return html;
            }

            @Override
            protected boolean contains(String search) {
                return mTextArea.getText().toLowerCase().contains(search) ||
                        mMessage.getUser().getName().toLowerCase().contains(search) ||
                        mMessage.getJID().toLowerCase().contains(search);
            }
        }
    }

    private static BufferedImage readImage(String path) {
        try {
             return ImageIO.read(new File(path));
        } catch(IOException ex) {
            LOGGER.log(Level.WARNING, "can't read image", ex);
            return new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        }
    }
}
