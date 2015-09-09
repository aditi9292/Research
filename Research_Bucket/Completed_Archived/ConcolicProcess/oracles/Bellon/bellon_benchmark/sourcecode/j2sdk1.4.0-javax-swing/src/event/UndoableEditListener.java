/*
 * @(#)UndoableEditListener.java       1.14 01/12/03
 *
 * Copyright 2002 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package javax.swing.event;
import javax.swing.undo.*;
/**
 * Interface implemented by a class interested in hearing about
 * undoable operations.
 *
 * @version 1.14 12/03/01
 * @author Ray Ryan
 */
public interface UndoableEditListener extends java.util.EventListener {
    /**
     * An undoable edit happened
     */
    void undoableEditHappened(UndoableEditEvent e); }
