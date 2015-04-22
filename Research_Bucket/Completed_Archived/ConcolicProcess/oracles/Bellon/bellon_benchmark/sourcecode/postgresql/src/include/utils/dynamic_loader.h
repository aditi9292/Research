/*-------------------------------------------------------------------------
 *
 * dynamic_loader.h
 *
 *
 *
 * Portions Copyright (c) 1996-2001, PostgreSQL Global Development Group
 * Portions Copyright (c) 1994, Regents of the University of California
 *
 * $Id: dynamic_loader.h,v 1.20 2001/11/05 17:46:36 momjian Exp $
 *
 *-------------------------------------------------------------------------
 */
#ifndef DYNAMIC_LOADER_H
#define DYNAMIC_LOADER_H
#include "fmgr.h"
extern void *pg_dlopen(char *filename);
extern PGFunction pg_dlsym(void *handle, char *funcname);
extern void pg_dlclose(void *handle);
extern char *pg_dlerror(void);
#endif   /* DYNAMIC_LOADER_H */