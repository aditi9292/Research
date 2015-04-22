/*
 *     cook - file construction tool
 *     Copyright (C) 1994, 1997, 1998, 1999 Peter Miller;
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
 * MANIFEST: functions to implement the split builtin function
 */
#include <builtin/split.h>
#include <error_intl.h>
#include <expr/position.h>
#include <str_list.h>
#include <trace.h>
static int interpret _((string_list_ty *, const string_list_ty *,
       const expr_position_ty *, const struct opcode_context_ty *));
static int
interpret(result, arg, pp, ocp)
       string_list_ty        *result;
       const string_list_ty *arg;
       const expr_position_ty *pp;
       const struct opcode_context_ty *ocp; {
       size_t               j;
       trace(("split\n"));
       if (arg->nstrings < 2) {
             sub_context_ty       *scp;
             scp = sub_context_new();
             sub_var_set(scp, "Name", "%S", arg->string[0]);
             error_with_position
             (
                  pp,
                  scp,
                  i18n("$name: requires one or more arguments")
             );
             sub_context_delete(scp);
             return -1; }
       for (j = 2; j < arg->nstrings; ++j) {
             string_list_ty       wl;
             size_t             k;
             str2wl(&wl, arg->string[j], arg->string[1]->str_text, 0);
             for (k = 0; k < wl.nstrings; ++k)
                  string_list_append(result, wl.string[k]);
             string_list_destructor(&wl); }
       return 0; }
builtin_ty builtin_split = {
       "split",
       interpret,
       interpret, /* script */
};
