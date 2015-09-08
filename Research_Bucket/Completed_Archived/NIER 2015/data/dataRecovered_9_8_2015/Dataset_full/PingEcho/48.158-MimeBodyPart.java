/**
 *
 * Copyright 2003-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package javax.mail.internet;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Enumeration;
import javax.activation.DataHandler;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.HeaderTokenizer.Token;

import org.apache.geronimo.mail.util.SessionUtil;
import org.apache.geronimo.mail.util.ASCIIUtil;

/**
 * @version $Rev: 390530 $ $Date: 2006-03-31 16:43:14 -0600 (Fri, 31 Mar 2006) $
 */
public class MimeBodyPart extends BodyPart implements MimePart {
	 // constants for accessed properties
    protected static final String MIME_DECODEFILENAME = "mail.mime.decodefilename";
    protected static final String MIME_SETDEFAULTTEXTCHARSET = "mail.mime.setdefaulttextcharset";


    /**
     * The {@link DataHandler} for this Message's content.
     */
    protected DataHandler dh;
    /**
     * This message's content (unless sourced from a SharedInputStream).
     */
    protected byte content[];
    /**
     * If the data for this message was supplied by a {@link SharedInputStream}
     * then this is another such stream representing the content of this message;
     * if this field is non-null, then {@link #content} will be null.
     */
    protected InputStream contentStream;
    /**
     * This message's headers.
     */
    protected InternetHeaders headers;

    public MimeBodyPart() {
        headers = new InternetHeaders();
    }

    public MimeBodyPart(InputStream in) throws MessagingException {
        headers = new InternetHeaders(in);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count;
        try {
            while((count = in.read(buffer, 0, 1024)) != -1)
                baos.write(buffer, 0, count);
        } catch (IOException e) {
            throw new MessagingException(e.toString(),e);
        }
        content = baos.toByteArray();
    }

    public MimeBodyPart(InternetHeaders headers, byte[] content) throws MessagingException {
        this.headers = headers;
        this.content = content;
    }

    /**
     * Return the content size of this message.  This is obtained
     * either from the size of the content field (if available) or
     * from the contentStream, IFF the contentStream returns a positive
     * size.  Returns -1 if the size is not available.
     *
     * @return Size of the content in bytes.
     * @exception MessagingException
     */
    public int getSize() throws MessagingException {
        if (content != null) {
            return content.length;
        }
        if (contentStream != null) {
            try {
                int size = contentStream.available();
                if (size > 0) {
                    return size;
                }
            } catch (IOException e) {
            }
        }
        return -1;
    }

    public int getLineCount() throws MessagingException {
        return -1;
    }

    public String getContentType() throws MessagingException {
        String value = getSingleHeader("Content-Type");
        if (value == null) {
            value = "text/plain";
        }
        return value;
    }

    /**
     * Tests to see if this message has a mime-type match with the
     * given type name.
     *
     * @param type   The tested type name.
     *
     * @return If this is a type match on the primary and secondare portion of the types.
     * @exception MessagingException
     */
    public boolean isMimeType(String type) throws MessagingException {
        return new ContentType(getContentType()).match(type);
    }

    /**
     * Retrieve the message "Content-Disposition" header field.
     * This value represents how the part should be represented to
     * the user.
     *
     * @return The string value of the Content-Disposition field.
     * @exception MessagingException
     */
    public String getDisposition() throws MessagingException {
        String disp = getSingleHeader("Content-Disposition");
        if (disp != null) {
            return new ContentDisposition(disp).getDisposition();
        }
        return null;
    }

    /**
     * Set a new dispostion value for the "Content-Disposition" field.
     * If the new value is null, the header is removed.
     *
     * @param disposition
     *               The new disposition value.
     *
     * @exception MessagingException
     */
    public void setDisposition(String disposition) throws MessagingException {
        if (disposition == null) {
            removeHeader("Content-Disposition");
        }
        else {
            // the disposition has parameters, which we'll attempt to preserve in any existing header.
            String currentHeader = getSingleHeader("Content-Disposition");
            if (currentHeader != null) {
                ContentDisposition content = new ContentDisposition(currentHeader);
                content.setDisposition(disposition);
                setHeader("Content-Disposition", content.toString());
            }
            else {
                // set using the raw string.
                setHeader("Content-Disposition", disposition);
            }
        }
    }

    /**
     * Retrieves the current value of the "Content-Transfer-Encoding"
     * header.  Returns null if the header does not exist.
     *
     * @return The current header value or null.
     * @exception MessagingException
     */
    public String getEncoding() throws MessagingException {
        // this might require some parsing to sort out.
        String encoding = getSingleHeader("Content-Transfer-Encoding");
        if (encoding != null) {
            // we need to parse this into ATOMs and other constituent parts.  We want the first
            // ATOM token on the string.
            HeaderTokenizer tokenizer = new HeaderTokenizer(encoding, HeaderTokenizer.MIME);

            Token token = tokenizer.next();
            while (token.getType() != Token.EOF) {
                // if this is an ATOM type, return it.
                if (token.getType() == Token.ATOM) {
                    return token.getValue();
                }
            }
            // not ATOMs found, just return the entire header value....somebody might be able to make sense of
            // this.
            return encoding;
        }
        // no header, nothing to return.
        return null;
    }


    /**
     * Retrieve the value of the "Content-ID" header.  Returns null
     * if the header does not exist.
     *
     * @return The current header value or null.
     * @exception MessagingException
     */
    public String getContentID() throws MessagingException {
        return getSingleHeader("Content-ID");
    }

    public void setContentID(String cid) throws MessagingException {
        setOrRemoveHeader("Content-ID", cid);
    }

    public String getContentMD5() throws MessagingException {
        return getSingleHeader("Content-MD5");
    }

    public void setContentMD5(String md5) throws MessagingException {
        setHeader("Content-MD5", md5);
    }

    public String[] getContentLanguage() throws MessagingException {
        return getHeader("Content-Language");
    }

    public void setContentLanguage(String[] languages) throws MessagingException {
        if (languages == null) {
            removeHeader("Content-Language");
        } else if (languages.length == 1) {
            setHeader("Content-Language", languages[0]);
        } else {
            StringBuffer buf = new StringBuffer(languages.length * 20);
            buf.append(languages[0]);
            for (int i = 1; i < languages.length; i++) {
                buf.append(',').append(languages[i]);
            }
            setHeader("Content-Language", buf.toString());
        }
    }

    public String getDescription() throws MessagingException {
        String description = getSingleHeader("Content-Description");
        if (description != null) {
            try {
                // this could be both folded and encoded.  Return this to usable form.
                return MimeUtility.decodeText(ASCIIUtil.unfold(description));
            } catch (UnsupportedEncodingException e) {
                // ignore
            }
        }
        // return the raw version for any errors.
        return description;
    }

    public void setDescription(String description) throws MessagingException {
        setDescription(description, null);
    }

    public void setDescription(String description, String charset) throws MessagingException {
        if (description == null) {
            removeHeader("Content-Description");
        }
        else {
            try {
                setHeader("Content-Description", ASCIIUtil.fold(21, MimeUtility.encodeText(description, charset, null)));
            } catch (UnsupportedEncodingException e) {
                throw new MessagingException(e.getMessage(), e);
            }
        }
    }

    public String getFileName() throws MessagingException {
        // see if there is a disposition.  If there is, parse off the filename parameter.
        String disposition = getDisposition();
        String filename = null;

        if (disposition != null) {
            filename = new ContentDisposition(disposition).getParameter("filename");
        }

        // if there's no filename on the disposition, there might be a name parameter on a
        // Content-Type header.
        if (filename == null) {
            String type = getContentType();
            if (type != null) {
                try {
                    filename = new ContentType(type).getParameter("name");
                } catch (ParseException e) {
                }
            }
        }
        // if we have a name, we might need to decode this if an additional property is set.
        if (filename != null && SessionUtil.getBooleanProperty(MIME_DECODEFILENAME, false)) {
            try {
                filename = MimeUtility.decodeText(filename);
            } catch (UnsupportedEncodingException e) {
                throw new MessagingException("Unable to decode filename", e);
            }
        }

        return filename;
    }


    public void setFileName(String name) throws MessagingException {
        // there's an optional session property that requests file name encoding...we need to process this before
        // setting the value.
        if (name == null) {
            try {
                name = MimeUtility.encodeText(name);
            } catch (UnsupportedEncodingException e) {
                throw new MessagingException("Unable to encode filename", e);
            }
        }

        // get the disposition string.
        String disposition = getDisposition();
        // if not there, then this is an attachment.
        if (disposition == null) {
            disposition = Part.ATTACHMENT;
        }
        // now create a disposition object and set the parameter.
        ContentDisposition contentDisposition = new ContentDisposition(disposition);
        contentDisposition.setParameter("filename", name);

        // serialize this back out and reset.
        setDisposition(contentDisposition.toString());
    }

    public InputStream getInputStream() throws MessagingException, IOException {
        return getDataHandler().getInputStream();
    }

    protected InputStream getContentStream() throws MessagingException {
        if (contentStream != null) {
            return contentStream;
        }

        if (content != null) {
            return new ByteArrayInputStream(content);
        } else {
            throw new MessagingException("No content");
        }
    }

    public InputStream getRawInputStream() throws MessagingException {
        return getContentStream();
    }

    public synchronized DataHandler getDataHandler() throws MessagingException {
        if (dh == null) {
            dh = new DataHandler(new MimePartDataSource(this));
        }
        return dh;
    }

    public Object getContent() throws MessagingException, IOException {
        return getDataHandler().getContent();
    }

    public void setDataHandler(DataHandler handler) throws MessagingException {
        dh = handler;
    }

    public void setContent(Object content, String type) throws MessagingException {
        setDataHandler(new DataHandler(content, type));
    }

    public void setText(String text) throws MessagingException {
        setText(text, null);
    }

    public void setText(String text, String charset) throws MessagingException {
        // we need to sort out the character set if one is not provided.
        if (charset == null) {
            // if we have non us-ascii characters here, we need to adjust this.
            if (!ASCIIUtil.isAscii(text)) {
                charset = MimeUtility.getDefaultMIMECharset();
            }
            else {
                charset = "us-ascii";
            }
        }
        setContent(text, "text/plain; charset=" + MimeUtility.quote(charset, HeaderTokenizer.MIME));
    }

    public void setContent(Multipart part) throws MessagingException {
        setDataHandler(new DataHandler(part, part.getContentType()));
        part.setParent(this);
    }

    public void writeTo(OutputStream out) throws IOException, MessagingException {
        headers.writeTo(out, null);
        // add the separater between the headers and the data portion.
        out.write('\r');
        out.write('\n');
        // we need to process this using the transfer encoding type
        OutputStream encodingStream = MimeUtility.encode(out, getEncoding()); 
        getDataHandler().writeTo(encodingStream);
        encodingStream.flush();
    }

    public String[] getHeader(String name) throws MessagingException {
        return headers.getHeader(name);
    }

    public String getHeader(String name, String delimiter) throws MessagingException {
        return headers.getHeader(name, delimiter);
    }

    public void setHeader(String name, String value) throws MessagingException {
        headers.setHeader(name, value);
    }

    /**
     * Conditionally set or remove a named header.  If the new value
     * is null, the header is removed.
     *
     * @param name   The header name.
     * @param value  The new header value.  A null value causes the header to be
     *               removed.
     *
     * @exception MessagingException
     */
    private void setOrRemoveHeader(String name, String value) throws MessagingException {
        if (value == null) {
            headers.removeHeader(name);
        }
        else {
            headers.setHeader(name, value);
        }
    }

    public void addHeader(String name, String value) throws MessagingException {
        headers.addHeader(name, value);
    }

    public void removeHeader(String name) throws MessagingException {
        headers.removeHeader(name);
    }

    public Enumeration getAllHeaders() throws MessagingException {
        return headers.getAllHeaders();
    }

    public Enumeration getMatchingHeaders(String[] name) throws MessagingException {
        return headers.getMatchingHeaders(name);
    }

    public Enumeration getNonMatchingHeaders(String[] name) throws MessagingException {
        return headers.getNonMatchingHeaders(name);
    }

    public void addHeaderLine(String line) throws MessagingException {
        headers.addHeaderLine(line);
    }

    public Enumeration getAllHeaderLines() throws MessagingException {
        return headers.getAllHeaderLines();
    }

    public Enumeration getMatchingHeaderLines(String[] names) throws MessagingException {
        return headers.getMatchingHeaderLines(names);
    }

    public Enumeration getNonMatchingHeaderLines(String[] names) throws MessagingException {
        return headers.getNonMatchingHeaderLines(names);
    }

    protected void updateHeaders() throws MessagingException {
        DataHandler handler = getDataHandler();

        try {
            // figure out the content type.  If not set, we'll need to figure this out.
            String type = dh.getContentType();
            // parse this content type out so we can do matches/compares.
            ContentType content = new ContentType(type);
            // is this a multipart content?
            if (content.match("multipart/*")) {
                // the content is suppose to be a MimeMultipart.  Ping it to update it's headers as well.
                try {
                    MimeMultipart part = (MimeMultipart)handler.getContent();
                    part.updateHeaders();
                } catch (ClassCastException e) {
                    throw new MessagingException("Message content is not MimeMultipart", e);
                }
            }
            else if (!content.match("message/rfc822")) {
                // simple part, we need to update the header type information
                // if no encoding is set yet, figure this out from the data handler.
                if (getHeader("Content-Transfer-Encoding") == null) {
                    setHeader("Content-Transfer-Encoding", MimeUtility.getEncoding(handler));
                }

                // is a content type header set?  Check the property to see if we need to set this.
                if (getHeader("Content-Type") == null) {
                    if (SessionUtil.getBooleanProperty(MIME_SETDEFAULTTEXTCHARSET, true)) {
                        // is this a text type?  Figure out the encoding and make sure it is set.
                        if (content.match("text/*")) {
                            // the charset should be specified as a parameter on the MIME type.  If not there,
                            // try to figure one out.
                            if (content.getParameter("charset") == null) {

                                String encoding = getEncoding();
                                // if we're sending this as 7-bit ASCII, our character set need to be
                                // compatible.
                                if (encoding != null && encoding.equalsIgnoreCase("7bit")) {
                                    content.setParameter("charset", "us-ascii");
                                }
                                else {
                                    // get the global default.
                                    content.setParameter("charset", MimeUtility.getDefaultMIMECharset());
                                }
                            }
                        }
                    }
                }
            }

            // if we don't have a content type header, then create one.
            if (getSingleHeader("Content-Type") == null) {
                // get the disposition header, and if it is there, copy the filename parameter into the
                // name parameter of the type.
                String disp = getHeader("Content-Disposition", null);
                if (disp != null) {
                    // parse up the string value of the disposition
                    ContentDisposition disposition = new ContentDisposition(disp);
                    // now check for a filename value
                    String filename = disposition.getParameter("filename");
                    // copy and rename the parameter, if it exists.
                    if (filename != null) {
                        content.setParameter("name", filename);
                    }
                }
                // set the header with the updated content type information.
                setHeader("Content-Type", content.toString());
            }

        } catch (IOException e) {
            throw new MessagingException("Error updating message headers", e);
        }
    }

    private String getSingleHeader(String name) throws MessagingException {
        String[] values = getHeader(name);
        if (values == null || values.length == 0) {
            return null;
        } else {
            return values[0];
        }
    }
}
