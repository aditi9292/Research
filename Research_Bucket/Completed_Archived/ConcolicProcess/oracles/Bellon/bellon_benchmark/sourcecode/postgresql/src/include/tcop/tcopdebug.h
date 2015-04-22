/*-------------------------------------------------------------------------
 *
 * tcopdebug.h
 *       #defines governing debugging behaviour in the traffic cop
 *
 *
 * Portions Copyright (c) 1996-2001, PostgreSQL Global Development Group
 * Portions Copyright (c) 1994, Regents of the University of California
 *
 * $Id: tcopdebug.h,v 1.9 2001/11/05 17:46:36 momjian Exp $
 *
 *-------------------------------------------------------------------------
 */
#ifndef TCOPDEBUG_H
#define TCOPDEBUG_H
/* ----------------------------------------------------------------
 *         debugging defines.
 *
 *         If you want certain debugging behaviour, then #define
 *         the variable to 1, else #undef it. -cim 10/26/89
 * ----------------------------------------------------------------
 */
/* ----------------
 *         TCOP_SHOWSTATS controls whether or not buffer and
 *         access method statistics are shown for each query. -cim 2/9/89
 * ----------------
 */
#undef TCOP_SHOWSTATS
/* ----------------
 *         TCOP_DONTUSENEWLINE controls the default setting of
 *         the UseNewLine variable in postgres.c
 * ----------------
 */
#undef TCOP_DONTUSENEWLINE
/* ----------------------------------------------------------------
 *         #defines controlled by above definitions
 * ----------------------------------------------------------------
 */
#endif   /* TCOPDEBUG_H */