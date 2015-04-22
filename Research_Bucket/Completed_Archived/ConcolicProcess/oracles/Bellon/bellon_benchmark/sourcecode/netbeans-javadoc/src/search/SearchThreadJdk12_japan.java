/*
 *                 Sun Public License Notice
 * 
 * The contents of this file are subject to the Sun Public License
 * Version 1.0 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://www.sun.com/
 * 
 * The Original Code is NetBeans. The Initial Developer of the Original
 * Code is Sun Microsystems, Inc. Portions Copyright 1997-2000 Sun
 * Microsystems, Inc. All Rights Reserved.
 */
package org.netbeans.modules.javadoc.search;
import java.util.StringTokenizer;
import java.util.ResourceBundle;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import javax.swing.text.html.parser.ParserDelegator;
import javax.swing.DefaultListModel;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.HTML;
import javax.swing.text.MutableAttributeSet;
import org.openide.TopManager;
import org.openide.util.NbBundle;
import org.openide.filesystems.FileObject;
/** This class implements the index search through documenation
 * generated by Jdk 1.2 standard doclet
 */
class SearchThreadJdk12_japan extends IndexSearchThread {
    private BufferedReader in;
    private URL contextURL;
    private boolean stopSearch = false;
    private boolean splitedIndex = false;
    private int currentIndexNumber;
    private FileObject folder = null;
    private String JapanEncoding;
    public SearchThreadJdk12_japan(String toFind, FileObject fo, IndexSearchThread.DocIndexItemConsumer diiConsumer, boolean caseSensitive, String JapanEncoding) {
        super( toFind, fo, diiConsumer, caseSensitive );        
       this.JapanEncoding = JapanEncoding;
        if ( fo.isFolder() ) {
            // Documentation uses splited index - resolve the right file
            // This is just a try in most cases the fileNumber should be
            // the right one but when some index files are missing we have
            // to find the right one
            folder = fo;
            currentIndexNumber = (int)(Character.toUpperCase( lastField.charAt(0) ))  - 'A' + 1;//toFind.charAt(0) ))  - 'A' + 1;
            if ( currentIndexNumber < 1 ) {
                currentIndexNumber = 1; }
            else if ( currentIndexNumber > 26 ) {
                currentIndexNumber = 27; }
            /*
            if ( currentIndexNumber < 1 || currentIndexNumber > 26 ) {
                currentIndexNumber = 27; }
            */
            findFileObject( 0 );
            splitedIndex = true; }
        else {
            try {
                contextURL = this.fo.getURL();
                 }//contextURL = this.fo.getParent().getURL();
            catch ( org.openide.filesystems.FileStateInvalidException e ) {
                throw new InternalError( "Can't create documentation folder URL - file state invalid" );  }// NOI18N
            splitedIndex = false; } }
    public void stopSearch() {
        stopSearch = true;
        try {
            in.close(); }
        catch ( java.io.IOException e ) {
            TopManager.getDefault().notifyException( e ); } }
    public void run () {
        ParserDelegator pd = new ParserDelegator();
        if ( fo == null || lastField == null || lastField.length() == 0) {
            taskFinished();
            return; }
        SearchCallbackJdk12_japan sc = null;
        int theDirection = 0;
        do {
            if ( sc != null ) {
                if (sc.badFile != theDirection ) {
                    break; }
                findFileObject( sc.badFile );
                if ( fo == null ) {
                    // No other file to search
                    break; } }
            try {    
                in = new BufferedReader( new InputStreamReader( fo.getInputStream (), JapanEncoding ));        
             // System.out.println("Encoding: " + JapanEncoding);
                pd.parse( in, sc = new SearchCallbackJdk12_japan( splitedIndex, caseSensitive ), true ); }
            catch ( java.io.IOException e ) {
                }// Do nothing
            if ( sc.badFile != 0 && theDirection == 0 ) {
                theDirection = sc.badFile; } }
        while ( sc.badFile != 0 );
        try {
            in.close(); }
        catch ( java.io.IOException e ) {
             }// Do nothing
        //is.searchEnded();
        taskFinished(); }
    void findFileObject( int direction ) {
        if ( direction < 0 ) {
            currentIndexNumber--; }
        else if ( direction > 0 ) {
            currentIndexNumber++; }
        do {
            // Assure the only one direction of looking for Files
            if ( currentIndexNumber < 0 || currentIndexNumber > 27 ) {
                fo = null;
                return; }
            Integer fileNumber = new Integer( currentIndexNumber );
            String fileName = new String( "index-" + fileNumber.toString() ); // NOI18N
            if ( folder == null ) {
                fo = null;
                return; }
            fo = folder.getFileObject( fileName, "html" ); // NOI18N
            if ( fo != null ) {
                try {
                    contextURL = this.fo.getURL(); }
                catch ( org.openide.filesystems.FileStateInvalidException e ) {
                    throw new InternalError( "Can't create documentation folder URL - file state invalid" );  } }// NOI18N
            else {
                currentIndexNumber += direction > 0 ? 1 : -1; } }
        while ( fo == null ); }
    // Inner classes ------------------------------------------------------------------------------------
    /* These are constants for the inner class */
    static private final String STR_CLASS = ResourceUtils.getBundledString( "JDK12_CLASS" );       //NOI18N
    static private final String STR_INTERFACE = ResourceUtils.getBundledString( "JDK12_INTERFACE" );   //NOI18N
    static private final String STR_EXCEPTION = ResourceUtils.getBundledString( "JDK12_EXCEPTION" );   //NOI18N
    static private final String STR_CONSTRUCTOR = ResourceUtils.getBundledString( "JDK12_CONSTRUCTOR" );   //NOI18N
    static private final String STR_METHOD = ResourceUtils.getBundledString( "JDK12_METHOD" );   //NOI18N
    static private final String STR_ERROR = ResourceUtils.getBundledString( "JDK12_ERROR" );   //NOI18N
    static private final String STR_VARIABLE = ResourceUtils.getBundledString( "JDK12_VARIABLE" );   //NOI18N
    static private final String STR_STATIC = ResourceUtils.getBundledString( "JDK12_STATIC" );   //NOI18N
    static private final String STR_DASH = ResourceUtils.getBundledString( "JDK12_DASH" );   //NOI18N
    static private final String STR_PACKAGE = ResourceUtils.getBundledString( "JDK12_PACKAGE" );   //NOI18N
    static private final String STR_CONSTRUCTOR_JA = ResourceUtils.getBundledString( "JDK12_CONSTRUCTOR_JA" );   //NOI18N
    static private final String STR_METHOD_JA = ResourceUtils.getBundledString( "JDK12_METHOD_JA" );   //NOI18N
    static private final String STR_VARIABLE_JA = ResourceUtils.getBundledString( "JDK12_VARIABLE_JA" );   //NOI18N
    static private final int IN_BALAST = 0;
    static private final int IN_DT = 1;
    static private final int IN_AREF = 2;
    static private final int IN_B = 3;
    static private final int IN_DESCRIPTION = 4;
    static private final int IN_DESCRIPTION_SUFFIX = 5;
    /** This inner class parses the JDK 1.2 Documentation index and returns
     *  found indexItems. 
     */
    private class SearchCallbackJdk12_japan extends HTMLEditorKit.ParserCallback {
        private String              hrefVal;
        private DocIndexItem        currentDii = null;
        private int                 where = IN_BALAST;
        private boolean             splited;
        private boolean             stopOnNext = false;
        private int                 badFile = 0;         
        int printText = 0;
        SearchCallbackJdk12_japan( boolean splited, boolean caseSensitive ) {
            super();
            this.splited = splited; }
        public void handleStartTag(HTML.Tag t, MutableAttributeSet a, int pos) {
            if ( t == HTML.Tag.DT ) {
                where = IN_DT;
                currentDii = null; }
            else if ( t == HTML.Tag.A && where == IN_DT ) {
                where = IN_AREF;
                Object val = a.getAttribute( HTML.Attribute.HREF );
                if ( val != null ) {
                    hrefVal = (String) val.toString();
                    currentDii = new DocIndexItem( null, null, contextURL, hrefVal ); } }
            else if ( t == HTML.Tag.A && where == IN_DESCRIPTION_SUFFIX ) {
                ;  }// Just ignore
            else if ( t == HTML.Tag.B && where == IN_AREF ) {
                where = IN_AREF; }
            else {
                where = IN_BALAST; } }
        public void handleText(char[] data, int pos) {
            if ( where == IN_AREF ) {
                if ( stopOnNext ) {
                    try {
                        in.close();
                        where = IN_BALAST;
                        return; }
                    catch ( java.io.IOException e ) {
                        TopManager.getDefault().notifyException( e ); } }
                String text = new String( data );
                if ( splited ) {
                    // it is possible that we search wrong file
                    char first = Character.toUpperCase( lastField.charAt( 0 ) );
                    char curr = Character.toUpperCase( data[0] );
                    if ( first != curr ) {
                        badFile = first < curr ? -1 : 1;
                        try {
                           in.close();
                           where = IN_BALAST;
                           return; }
                        catch ( java.io.IOException e ) {
                            TopManager.getDefault().notifyException( e ); } } }
                currentDii.setField( text.trim() );
                    where = IN_DESCRIPTION; }
            else if ( where == IN_DESCRIPTION  ) {
                String text = new String( data );      
                /*
                // Stop suffering if we are behind the searched words
                if ( text.substring( 0, Math.min(toFind.length(), text.length()) ).compareTo( toFind ) > 0 ) {
                    try {
                        System.out.println("Stoping suffering");
                        in.close(); }
                    catch ( java.io.IOException e ) {
                        TopManager.getDefault().notifyException( e ); } }
                */
                currentDii.setRemark( text );
                StringTokenizer st = new StringTokenizer( text );
                String token = st.nextToken();
                if ( token.equals( STR_DASH ) )
                    token = st.nextToken();
                boolean isStatic = false;
                if ( token.equalsIgnoreCase( STR_STATIC ) ) {
                    isStatic = true;
                    token = st.nextToken(); }
                if ( token.equalsIgnoreCase( STR_CLASS ) )
                    currentDii.setIconIndex( DocSearchIcons.ICON_CLASS );
                else if ( token.equalsIgnoreCase( STR_INTERFACE ) )
                    currentDii.setIconIndex( DocSearchIcons.ICON_INTERFACE );
                else if ( token.equalsIgnoreCase( STR_EXCEPTION ) )
                    currentDii.setIconIndex( DocSearchIcons.ICON_EXCEPTION );
                else if ( token.equalsIgnoreCase( STR_ERROR ) )
                    currentDii.setIconIndex( DocSearchIcons.ICON_ERROR );
                else if ( token.equalsIgnoreCase( STR_PACKAGE ) )
                    currentDii.setIconIndex( DocSearchIcons.ICON_PACKAGE );
                else if ( token.equalsIgnoreCase( STR_CONSTRUCTOR ) )
                    currentDii.setIconIndex( DocSearchIcons.ICON_CONSTRUCTOR );
                else if ( token.equalsIgnoreCase( STR_METHOD ) )
                    currentDii.setIconIndex( isStatic ? DocSearchIcons.ICON_METHOD_ST : DocSearchIcons.ICON_METHOD );
                else if ( token.equalsIgnoreCase( STR_VARIABLE ) )
                    currentDii.setIconIndex( isStatic ? DocSearchIcons.ICON_VARIABLE_ST : DocSearchIcons.ICON_VARIABLE );
                // Add the item when all information is available
                //insertDocIndexItem( currentDii );
                if ( text.endsWith( "." ) ) { // NOI18N
                    where = IN_DESCRIPTION_SUFFIX;
                    currentDii.setPackage( text.substring( text.lastIndexOf( ' ' ) ).trim() ); }
                else
                    where = IN_BALAST; }
            else if ( where == IN_DESCRIPTION_SUFFIX ) {
                boolean isStatic = false;
                String text = new String ( data );
                currentDii.setRemark( currentDii.getRemark() + text);
                String declaringClass = new String( data ).trim();
                if( !(".".equals(declaringClass))){    //NOI18N
                    currentDii.setDeclaringClass(declaringClass);
                    // System.out.println("Data: " + text );
                    text = text.toUpperCase();
                    if( text.indexOf( STR_STATIC ) != -1 )
                        isStatic = true;
                    if( text.indexOf ( STR_CONSTRUCTOR_JA ) != -1 )
                        currentDii.setIconIndex (DocSearchIcons.ICON_CONSTRUCTOR );
                    else if( text.indexOf ( STR_METHOD_JA ) != -1 )
                        currentDii.setIconIndex ( isStatic ? DocSearchIcons.ICON_METHOD_ST : DocSearchIcons.ICON_METHOD );
                    else if( text.indexOf ( STR_VARIABLE_JA ) != -1 )
                        currentDii.setIconIndex ( isStatic ? DocSearchIcons.ICON_VARIABLE_ST : DocSearchIcons.ICON_VARIABLE );
                    insertDocIndexItem( currentDii ); } }
            else
                where = IN_BALAST; } } }