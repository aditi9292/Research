/*****************************************************************************
  FILE           : $Source: /projects/higgs1/SNNS/CVS/SNNS/xgui/sources/ui_result.h,v $
  SHORTNAME      : result.h
  SNNS VERSION   : 4.2
  PURPOSE        :
  NOTES          :
  AUTHOR         : Michael Vogt
  DATE           : 20. 5. 1992
  CHANGED BY     : Guenter Mamier
  RCS VERSION    : $Revision: 2.6 $
  LAST CHANGE    : $Date: 1998/02/25 15:22:33 $
    Copyright (c) 1990-1995  SNNS Group, IPVR, Univ. Stuttgart, FRG
    Copyright (c) 1996-1998  SNNS Group, WSI, Univ. Tuebingen, FRG
******************************************************************************/
#ifndef _UI_RESULT_DEFINED_
#define _UI_RESULT_DEFINED_
#define        UI_RESULT_INPUT_YES    0
#define UI_RESULT_INPUT_NO     1
#define        UI_RESULT_OUTPUT_YES   0
#define UI_RESULT_OUTPUT_NO    1
#define UI_RESULT_MODE_CREATE  0
#define UI_RESULT_MODE_APPEND  1
extern void ui_popupResult (Widget);
extern Bool ui_ResultIsCreated;
#endif /* _UI_RESULT_DEFINED_ */
/* 42 lines generated by deleteprivatedefinitions.awk */
