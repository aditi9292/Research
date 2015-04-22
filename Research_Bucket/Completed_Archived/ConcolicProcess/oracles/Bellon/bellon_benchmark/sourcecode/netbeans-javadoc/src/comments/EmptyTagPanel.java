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
package org.netbeans.modules.javadoc.comments;
import org.openide.src.MemberElement;
import org.openide.src.JavaDocTag;
/**
 *
 * @author
 * @version
 */
public class EmptyTagPanel extends TagPanel {
    private static final String cardName = "CRD_EMPTY"; // NOI18N
    static final long serialVersionUID =7707027689326548947L;
    /** Initializes the Form */
    public EmptyTagPanel( JavaDocEditorPanel editorPanel ) {
        super( editorPanel );
        initComponents ();
        initAccessibility(); }
    private void initAccessibility() {
        jLabel1.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getBundle(EmptyTagPanel.class).getString("ACS_EmptyTagPanel.jLabel1.textA11yDesc"));   }// NOI18N
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the FormEditor.
     */
    private void initComponents() {//GEN-BEGIN:initComponents
        jLabel1 = new javax.swing.JLabel();
        setLayout(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints gridBagConstraints1;
        jLabel1.setText(org.openide.util.NbBundle.getBundle(EmptyTagPanel.class).getString("CTL_EmptyTagPanel.jLabel1.text"));
        gridBagConstraints1 = new java.awt.GridBagConstraints();
        gridBagConstraints1.insets = new java.awt.Insets(2, 2, 2, 1);
        gridBagConstraints1.anchor = java.awt.GridBagConstraints.NORTHEAST;
        add(jLabel1, gridBagConstraints1);
    }//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel jLabel1;
    // End of variables declaration//GEN-END:variables
    void setData( JavaDocTag tag ) { }
    JavaDocTag getTag( String tagName ) {
        return null; }
    String getCardName() {
        return cardName; }
    void grabFirstFocus() {} }
