/*
 *	cook - file construction tool
 *	Copyright (C) 1997, 1999 Peter Miller;
 *	All rights reserved.
 *
 *	This program is free software; you can redistribute it and/or modify
 *	it under the terms of the GNU General Public License as published by
 *	the Free Software Foundation; either version 2 of the License, or
 *	(at your option) any later version.
 *
 *	This program is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *	GNU General Public License for more details.
 *
 *	You should have received a copy of the GNU General Public License
 *	along with this program; if not, write to the Free Software
 *	Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111, USA.
 *
 * MANIFEST: input for configure, template for etc/libdirh.h
 *
 *	Generated automatically from libdirh.h.in by configure.
 */

#ifndef AUX_LIBDIR_H
#define AUX_LIBDIR_H

/*
 * LIBDIR is for architecture-specific files
 *      On a network, this would only be shared between machines
 *      of identical cpu-hw-os flavour.  It can be read-only.
 */
#define LIBDIR "/usr/local/lib/cook"

/*
 * DATADIR is for architecture-neutral files
 *      On a network, this would be shared between all machines
 *      on the network.  It can be read-only.
 */
#define DATADIR "/usr/local/share/cook"

/*
 * MANDIR is for manual entries, architecture-neutral by definition
 *      On a network, this would be shared between all machines
 *      on the network.  It can be read-only.
 */
#define MANDIR "/usr/local/man"

/*
 * EXEEXT is the filename extension for executable files.
 */
#define EXEEXT ""

#endif /* AUX_LIBDIR_H */
