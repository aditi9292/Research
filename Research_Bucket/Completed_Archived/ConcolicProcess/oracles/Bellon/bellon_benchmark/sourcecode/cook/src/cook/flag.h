/*
 *     cook - file construction tool
 *     Copyright (C) 1997, 1998, 1999, 2001 Peter Miller;
 *     All rights reserved.
 *
 *     This program is free software; you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation; either version 2 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program; if not, write to the Free Software
 *     Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111, USA.
 *
 * MANIFEST: interface definition for cook/flag.c
 */
#ifndef COOK_FLAG_H
#define COOK_FLAG_H
#include <main.h>
struct expr_position_ty; /* existence */
struct string_list_ty; /* existence */
enum flag_value_ty {
       RF_CASCADE,
       RF_CASCADE_OFF,
       RF_CLEARSTAT,
       RF_CLEARSTAT_OFF,
       RF_DEFAULT,
       RF_DEFAULT_OFF,
       RF_ERROK,
       RF_ERROK_OFF,
       RF_FINGERPRINT,
       RF_FINGERPRINT_NOWRITE,
       RF_FINGERPRINT_OFF,
       RF_FORCE,
       RF_FORCE_OFF,
       RF_GATEFIRST,
       RF_GATEFIRST_OFF,
       RF_IMPLICIT_ALLOWED,
       RF_IMPLICIT_ALLOWED_OFF,
       RF_INCLUDE_COOKED_WARNING,
       RF_INCLUDE_COOKED_WARNING_OFF,
       RF_INGREDIENTS_FINGERPRINT,
       RF_INGREDIENTS_FINGERPRINT_OFF,
       RF_METER,
       RF_METER_OFF,
       RF_MKDIR,
       RF_MKDIR_OFF,
       RF_PRECIOUS,
       RF_PRECIOUS_OFF,
       RF_RECURSE,
       RF_RECURSE_OFF,
       RF_SHALLOW,
       RF_SHALLOW_OFF,
       RF_SILENT,
       RF_SILENT_OFF,
       RF_STRIPDOT,
       RF_STRIPDOT_OFF,
       RF_UNLINK,
       RF_UNLINK_OFF,
       RF_UPDATE,
       RF_UPDATE_MAX,
       RF_UPDATE_OFF,
       RF_MATCH_MODE_COOK,
       RF_MATCH_MODE_REGEX,
       RF_max        /* MUST be last */
};
typedef enum flag_value_ty flag_value_ty;
typedef struct flag_ty flag_ty;
struct flag_ty {
       unsigned char flag[RF_max];
};
flag_ty *flag_new _((void));
flag_ty *flag_copy _((const flag_ty *));
void flag_delete _((flag_ty *));
void flag_union _((flag_ty *, const flag_ty *));
flag_ty *flag_recognize _((const struct string_list_ty *,
       const struct expr_position_ty *));
void flag_set_options _((const flag_ty *, int));
int flag_query _((const flag_ty *, flag_value_ty));
#endif /* COOK_FLAG_H */
