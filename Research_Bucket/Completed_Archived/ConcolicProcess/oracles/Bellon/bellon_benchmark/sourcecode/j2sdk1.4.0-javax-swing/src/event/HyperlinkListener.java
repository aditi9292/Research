/*
 * @(#)HyperlinkListener.java  1.8 01/12/03
 *
 * Copyright 2002 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package javax.swing.event;
import java.util.EventListener;
/**
 * HyperlinkListener
 *
 * @version 1.8 12/03/01
 * @author  Timothy Prinzing
 */
public interface HyperlinkListener extends EventListener {
    /**
     * Called when a hypertext link is updated.
     *
     * @param e the event responsible for the update
     */
    void hyperlinkUpdate(HyperlinkEvent e); }
