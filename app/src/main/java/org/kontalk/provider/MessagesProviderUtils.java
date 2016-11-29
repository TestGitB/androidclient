/*
 * Kontalk Android client
 * Copyright (C) 2016 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.provider;

import java.io.File;
import java.util.Random;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import org.kontalk.crypto.Coder;
import org.kontalk.message.TextComponent;
import org.kontalk.provider.MyMessages.Groups;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.service.msgcenter.group.KontalkGroupController;
import org.kontalk.util.MessageUtils;


/**
 * Utility class for interacting with the {@link MessagesProvider}.
 * @author Daniele Ricci
 */
public class MessagesProviderUtils {

    private MessagesProviderUtils() {
    }

    /** Checks if the message lives. */
    public static boolean exists(Context context, long msgId) {
        boolean b = false;
        Cursor c = context.getContentResolver().
            query(ContentUris.withAppendedId(Messages.CONTENT_URI, msgId),
                null, null, null, null);
        if (c.moveToFirst())
            b = true;
        c.close();
        return b;
    }

    /** Inserts a new outgoing text message. */
    public static Uri newOutgoingMessage(Context context, String msgId, String userId,
            String text, boolean encrypted) {

        byte[] bytes = text.getBytes();
        ContentValues values = new ContentValues(11);
        // must supply a message ID...
        values.put(Messages.MESSAGE_ID, msgId);
        values.put(Messages.PEER, userId);
        values.put(Messages.BODY_MIME, TextComponent.MIME_TYPE);
        values.put(Messages.BODY_CONTENT, bytes);
        values.put(Messages.BODY_LENGTH, bytes.length);
        values.put(Messages.UNREAD, false);
        values.put(Messages.DIRECTION, Messages.DIRECTION_OUT);
        values.put(Messages.TIMESTAMP, System.currentTimeMillis());
        values.put(Messages.STATUS, Messages.STATUS_SENDING);
        // of course outgoing messages are not encrypted in database
        values.put(Messages.ENCRYPTED, false);
        values.put(Messages.SECURITY_FLAGS, encrypted ? Coder.SECURITY_BASIC : Coder.SECURITY_CLEARTEXT);
        return context.getContentResolver().insert(
            Messages.CONTENT_URI, values);
    }

    /** Inserts a new outgoing binary message. */
    public static Uri newOutgoingMessage(Context context, String msgId, String userId,
            String mime, Uri uri, long length, int compress, File previewFile, boolean encrypted) {
        ContentValues values = new ContentValues(13);
        values.put(Messages.MESSAGE_ID, msgId);
        values.put(Messages.PEER, userId);

        /* TODO one day we'll ask for a text to send with the image
        values.put(Messages.BODY_MIME, TextComponent.MIME_TYPE);
        values.put(Messages.BODY_CONTENT, content.getBytes());
        values.put(Messages.BODY_LENGTH, content.length());
         */

        values.put(Messages.UNREAD, false);
        // of course outgoing messages are not encrypted in database
        values.put(Messages.ENCRYPTED, false);
        values.put(Messages.SECURITY_FLAGS, encrypted ? Coder.SECURITY_BASIC : Coder.SECURITY_CLEARTEXT);
        values.put(Messages.DIRECTION, Messages.DIRECTION_OUT);
        values.put(Messages.TIMESTAMP, System.currentTimeMillis());
        values.put(Messages.STATUS, Messages.STATUS_QUEUED);

        if (previewFile != null)
            values.put(Messages.ATTACHMENT_PREVIEW_PATH, previewFile.getAbsolutePath());

        values.put(Messages.ATTACHMENT_MIME, mime);
        values.put(Messages.ATTACHMENT_LOCAL_URI, uri.toString());
        values.put(Messages.ATTACHMENT_LENGTH, length);
        values.put(Messages.ATTACHMENT_COMPRESS, compress);

        return context.getContentResolver().insert(Messages.CONTENT_URI, values);
    }

    /** Returns the thread associated with the given message. */
    public static long getThreadByMessage(Context context, Uri message) {
        Cursor c = context.getContentResolver().query(message,
            new String[] { Messages.THREAD_ID }, null, null,
            null);
        try {
            if (c.moveToFirst())
                return c.getLong(0);

            return Messages.NO_THREAD;
        }
        finally {
            c.close();
        }
    }

    public static int updateDraft(Context context, long threadId, String draft) {
        ContentValues values = new ContentValues(1);
        if (draft != null && draft.length() > 0)
            values.put(Threads.DRAFT, draft);
        else
            values.putNull(Threads.DRAFT);
        return context.getContentResolver().update(
            ContentUris.withAppendedId(Threads.CONTENT_URI, threadId),
            values, null, null);
    }

    /**
     * Fills a media message with preview file and local uri, for use e.g.
     * after compressing. Also updates the message status to SENDING.
     */
    public static int updateMedia(Context context, long id, String previewFile, Uri localUri, long length) {
        ContentValues values = new ContentValues(3);
        values.put(Messages.ATTACHMENT_PREVIEW_PATH, previewFile);
        values.put(Messages.ATTACHMENT_LOCAL_URI, localUri.toString());
        values.put(Messages.ATTACHMENT_LENGTH, length);
        values.put(Messages.STATUS, Messages.STATUS_SENDING);
        return context.getContentResolver().update(ContentUris
            .withAppendedId(Messages.CONTENT_URI, id), values, null, null);
    }

    public static int deleteMessage(Context context, long id) {
        return context.getContentResolver().delete(ContentUris
            .withAppendedId(Messages.CONTENT_URI, id), null, null);
    }

    public static boolean deleteThread(Context context, long id, boolean keepGroup) {
        ContentResolver c = context.getContentResolver();
        return (c.delete(ContentUris.withAppendedId(Threads.Conversations.CONTENT_URI, id)
            .buildUpon().appendQueryParameter(Messages.KEEP_GROUP, String.valueOf(keepGroup))
            .build(), null, null) > 0);
    }

    /** Marks the given message as SENDING, regardless of its current status. */
    public static int retryMessage(Context context, Uri uri, boolean encrypted) {
        ContentValues values = new ContentValues(2);
        values.put(Messages.STATUS, Messages.STATUS_SENDING);
        values.put(Messages.SECURITY_FLAGS, encrypted ? Coder.SECURITY_BASIC : Coder.SECURITY_CLEARTEXT);
        return context.getContentResolver().update(uri, values, null, null);
    }

    /** Marks all pending messages to the given recipient as SENDING. */
    public static int retryMessagesTo(Context context, String to) {
        Cursor c = context.getContentResolver().query(Messages.CONTENT_URI,
                new String[] { Messages._ID },
                Messages.PEER + "=? AND " + Messages.STATUS + "=" + Messages.STATUS_PENDING,
                new String[] { to },
                Messages._ID);

        while (c.moveToNext()) {
            long msgID = c.getLong(0);
            Uri msgURI = ContentUris.withAppendedId(Messages.CONTENT_URI, msgID);
            long threadID = getThreadByMessage(context, msgURI);
            if (threadID == Messages.NO_THREAD)
                continue;
            Uri threadURI = ContentUris.withAppendedId(Threads.CONTENT_URI, threadID);
            Cursor cThread = context.getContentResolver().query(threadURI,
                    new String[] { Threads.ENCRYPTION }, null, null,
                    null);
            if (cThread.moveToFirst()) {
                boolean encrypted = MessageUtils.sendEncrypted(context, cThread.getInt(0) != 0);
                retryMessage(context, msgURI, encrypted);
            }
            cThread.close();
        }
        c.close();
        return c.getCount();
    }

    /** Inserts an empty thread (that is, with no messages). */
    public static long insertEmptyThread(Context context, String peer, String draft) {
        ContentValues msgValues = new ContentValues(9);
        // must supply a message ID...
        msgValues.put(Messages.MESSAGE_ID, "draft" + (new Random().nextInt()));
        // use group id as the peer
        msgValues.put(Messages.PEER, peer);
        msgValues.put(Messages.BODY_CONTENT, new byte[0]);
        msgValues.put(Messages.BODY_LENGTH, 0);
        msgValues.put(Messages.BODY_MIME, TextComponent.MIME_TYPE);
        msgValues.put(Messages.DIRECTION, Messages.DIRECTION_OUT);
        msgValues.put(Messages.TIMESTAMP, System.currentTimeMillis());
        msgValues.put(Messages.ENCRYPTED, false);
        if (draft != null)
            msgValues.put(Threads.DRAFT, draft);
        Uri newThread = context.getContentResolver().insert(Messages.CONTENT_URI, msgValues);
        return newThread != null ? ContentUris.parseId(newThread) : Messages.NO_THREAD;
    }

    public static long createGroupThread(Context context, String groupJid, String subject, String[] members, String draft) {
        // insert group
        ContentValues values = new ContentValues();
        values.put(Groups.GROUP_JID, groupJid);

        // create new conversation
        long threadId = MessagesProviderUtils.insertEmptyThread(context, groupJid, draft);

        values.put(Groups.THREAD_ID, threadId);
        values.put(Groups.SUBJECT, subject);
        values.put(Groups.GROUP_TYPE, KontalkGroupController.GROUP_TYPE);
        context.getContentResolver().insert(Groups.CONTENT_URI, values);

        // remove values not for members table
        values.remove(Groups.GROUP_JID);
        values.remove(Groups.THREAD_ID);
        values.remove(Groups.SUBJECT);
        values.remove(Groups.GROUP_TYPE);

        // insert group members
        for (String member : members) {
            // FIXME turn this into batch operations
            values.put(Groups.PEER, member);
            context.getContentResolver()
                .insert(Groups.getMembersUri(groupJid), values);
        }

        return threadId;
    }

    public static void addGroupMembers(Context context, String groupJid, String[] members, boolean pending) {
        ContentValues values = new ContentValues();
        values.put(Groups.GROUP_JID, groupJid);
        for (String member : members) {
            // FIXME turn this into batch operations
            values.put(Groups.PEER, member);
            values.put(Groups.PENDING, pending ? Groups.MEMBER_PENDING_ADDED : 0);
            context.getContentResolver()
                .insert(Groups.getMembersUri(groupJid), values);
        }
    }

    public static void removeGroupMembers(Context context, String groupJid, String[] members, boolean pending) {
        if (pending) {
            ContentValues values = new ContentValues(1);
            values.put(Groups.PENDING, Groups.MEMBER_PENDING_REMOVED);
            for (String member : members) {
                // FIXME turn this into batch operations
                context.getContentResolver()
                    .update(Groups.getMembersUri(groupJid).buildUpon()
                        .appendPath(member).build(), values, null, null);
            }
        }
        else {
            for (String member : members) {
                // just beat it!
                context.getContentResolver()
                    .delete(Groups.getMembersUri(groupJid).buildUpon()
                        .appendPath(member).build(), null, null);
            }
        }
    }

    public static int setGroupSubject(Context context, String groupJid, String subject) {
        ContentValues values = new ContentValues();
        if (subject != null)
            values.put(Groups.SUBJECT, subject);
        else
            values.putNull(Groups.SUBJECT);

        return context.getContentResolver().update(Groups.getUri(groupJid),
            values, null, null);
    }

    public static boolean isGroupExisting(Context context, String groupJid) {
        Cursor c = context.getContentResolver().query(
            Groups.getUri(groupJid),
            new String[] { Groups.GROUP_JID }, null, null, null);
        boolean exist = c.moveToFirst();
        c.close();
        return exist;
    }

    public static String[] getGroupMembers(Context context, String groupJid, int flags) {
        String where;
        if (flags > 0) {
            where = "(" + Groups.PENDING + " & " + flags + ") = " + flags;
        }
        else if (flags == 0) {
            // handle zero flags special case (means all flags cleared)
            where = Groups.PENDING + "=0";
        }
        else {
            // any flag
            where = null;
        }

        Cursor c = context.getContentResolver()
            .query(Groups.getMembersUri(groupJid),
                new String[] { Groups.PEER },
                where, null, null);

        String[] members = new String[c.getCount()];
        int i = 0;
        while (c.moveToNext()) {
            members[i++] = c.getString(0);
        }
        c.close();
        return members;
    }

    public static int setGroupMembership(Context context, String groupJid, int membership) {
        ContentValues values = new ContentValues(1);
        values.put(Groups.MEMBERSHIP, membership);
        return context.getContentResolver().update(Groups.getUri(groupJid),
            values, null, null);
    }

    /** Returns the current known membership of a user in a group. */
    public static boolean isGroupMember(Context context, String groupJid, String jid) {
        Cursor c = null;
        try {
            c = context.getContentResolver().query(Groups.getMembersUri(groupJid),
                new String[] { Groups.PENDING },  Groups.PEER + "=?", new String[] { jid }, null);
            return c.moveToNext() && c.getInt(0) == 0;
        }
        finally {
            if (c != null)
                c.close();
        }
    }

    public static String[] parseThreadContent(String content) {
        String[] parsed = content.split(";", 2);
        if (parsed.length < 2) {
            return new String[] { null, content };
        }

        if (parsed[1].length() == 0)
            parsed[1] = null;

        return parsed;
    }

    public static int setEncryption(Context context, long threadId, boolean encryption) {
        ContentValues values = new ContentValues(1);
        values.put(Threads.ENCRYPTION, encryption);
        return context.getContentResolver().update(
                ContentUris.withAppendedId(Threads.CONTENT_URI, threadId),
                values, null, null);
    }

}
