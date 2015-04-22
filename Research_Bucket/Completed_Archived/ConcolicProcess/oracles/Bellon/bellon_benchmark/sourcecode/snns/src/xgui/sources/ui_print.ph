/*****************************************************************************
  FILE           : $Source: /projects/higgs1/SNNS/CVS/SNNS/xgui/sources/ui_print.ph,v $
  SHORTNAME      : print.ph
  SNNS VERSION   : 4.2
  PURPOSE        : header for ui_print.c
  NOTES          : all functions will be exported
  AUTHOR         : Ralf Huebner
  DATE           : 11.5.1992
  CHANGED BY     : Guenter Mamier
  RCS VERSION    : $Revision: 2.10 $
  LAST CHANGE    : $Date: 1998/02/25 15:22:31 $
    Copyright (c) 1990-1995  SNNS Group, IPVR, Univ. Stuttgart, FRG
    Copyright (c) 1996-1998  SNNS Group, WSI, Univ. Tuebingen, FRG
******************************************************************************/
#ifndef _UI_PRINT_DEFINED_
#define _UI_PRINT_DEFINED_
/* begin global definition section */
#ifndef ZERO
#define ZERO 0
#endif
#define min(a, b) ((a) < (b) ? (a) : (b))
#define UI_STR_LEN MAX_NAME_LENGTH
#define HEADER_COMPILE
       /* number of radio buttons in a group */
#define noOfFmtToggles     2
#define noOfPaperToggles   3
#define noOfDestToggles    2
#define noOfAsToggles      2
#define noOfClipToggles    2
#define noOfColorToggles   2
#define noOfOrientToggles  2
#define noOfAuToggles      2
#define noOfBorderToggles  2
#define noOfSizeToggles    2
#define noOfShapeToggles   2
#define noOfTextToggles    2
void ui_defaultPrinterValues (void);
void ui_printNet (void);
       /* global variables */
Ui_printerValType ui_prVal;   /* the structure that holds the values */
char *ui_tmpDir = "/tmp";     /* the directory for the temporary printer files */
char *ui_praefix = "snns";    /* the prefix for the temporary printer files */
FILE *psOutfile;              /* file id of the postscript output file */
/* end global definition section */
/* begin private definition section */
static void ui_valueToRGB (float, float *, float *, float *);
static void ui_printUnit (struct Ui_DisplayType *, int);
static void ui_printUnits (struct Ui_DisplayType *);
static void ui_getNetworkSize (int *, int *, int *, int *);
static void ui_printLink (struct Ui_DisplayType *, int, int, FlintType);
static void ui_printLinks (struct Ui_DisplayType *);
static char *ui_psBoolean (int);
static void ui_closeNetworkPannel (Widget, Widget, caddr_t);
static void ui_transferSettings (void);
static void ui_printProlog (void);
static void ui_printParams (void);
static void ui_printProc (void);
static void ui_closePrintPannel (Widget, Widget, caddr_t);
static void ui_setCmdLine (int);
static void ui_setDestProc (Widget, int, caddr_t);
static void ui_setPaperProc (Widget, int, caddr_t);
static void ui_setOrientProc (Widget, int, caddr_t);
static void ui_setAutoScaleProc (Widget, int, caddr_t);
static void ui_setAutoUniProc (Widget, int, caddr_t);
static void ui_setSizeProc (Widget, int, caddr_t);
static void ui_setShapeProc (Widget, int, caddr_t);
static void ui_setTextProc (Widget, int, caddr_t);
static void ui_setBorderProc (Widget, int, caddr_t);
static void ui_setColorProc (Widget, int, caddr_t);
static void ui_printSetupProc (Widget, Widget, caddr_t);
#ifdef _UNUSED_FUNCTIONS_
static void ui_readPrintHeader (void);
static void ui_setFormatProc (Widget, int, caddr_t);
static void ui_setClipProc (Widget, int, caddr_t);
#endif /* _UNUSED_FUNCTIONS_ */
       /* widgets for the pannels */
static Widget ui_printPannel;
static Widget paperToggle[noOfPaperToggles];
static Widget destToggle[noOfDestToggles];
static Widget autoScaleToggle[noOfAsToggles];
#ifdef _UNUSED_FUNCTIONS_
static Widget formatToggle[noOfFmtToggles];
static Widget clipToggle[noOfClipToggles];
#endif /* _UNUSED_FUNCTIONS_ */
static Widget colorToggle[noOfColorToggles];
static Widget orientToggle[noOfOrientToggles];
static Widget autoUniToggle[noOfAuToggles];
static Widget borderToggle[noOfBorderToggles];
static Widget sizeToggle[noOfSizeToggles];
static Widget shapeToggle[noOfShapeToggles];
static Widget textToggle[noOfTextToggles];
static Widget cmdLine, cmdLabel, borderVert, borderHoriz;
static Widget psxScale, psyScale, assDisplay, fillIntens;
static Widget ulyPos, lryPos, ulxPos, lrxPos;
       /* paper sizes for din a4, din a3 and us letter in mm */
static float paperFormats[3][2] = {{210, 297}, {297, 420}, {203.2, 279.4}}; 
static int networkXmin, networkYmin;             /* network size */
static int networkXmax, networkYmax;
static int ui_fontWidth = 8;                     /* font width */
static char *fileName;                           /* pointer to the file name */
static char *timeStr;                            /* current time and date  */
static Bool ui_printPannelIsOpen = FALSE;        /* indicates that the printer pannel is open */
static Bool ui_printDefaultsAreThere = FALSE;    /* indicates that the defaults are transfered */
static struct Ui_DisplayType  *displayPrintPtr;  /* pointer to the display to print */
       /* some postscript variables that are not set by the pannels */
static char *fo = "Helvetica";   /* used font */
static int ufs = 6;              /* font height for units */
static int wfs = 4;              /* font height for links */
static float ulw = 0.5;          /* unit border linewidth */
static float blw = 0.5;          /* frame border linewidth */
       /* the other postscript variables */
static char *ShapeCircle, *TransText, *Direction, *ShowWeight;
static char *ShowBorder, *ClipOnBorder, *ShowTop, *ShowBottom;
static char *FixedUnitSize, *LandscapeMode, *AutoScale, *AutoUni;
static int ms, rs, ulx, uly, lrx, lry;
static float ImageWidth, ImageHeight, TextRed, TextGreen, TextBlue;
static float BackgroundRed, BackgroundGreen, BackgroundBlue;
static float ph, pw, bh, bv, ug, isx, isy;
static float bb_xmin, bb_ymin, bb_xmax, bb_ymax;
       /* OLD UI_PRINTPS.C   ui_printps.c */
#ifndef HEADER_COMPILE
static int headerSize;
static char *psHeader;
#else
#ifdef _UNUSED_FUNCTIONS_
static int headerSize = 3354;
#endif
static char psHeader[] = {
    0x25, 0x25, 0x42, 0x65, 0x67, 0x69, 0x6e, 0x50, 
    0x72, 0x6f, 0x63, 0x53, 0x65, 0x74, 0x3a, 0x20, 
    0x53, 0x4e, 0x4e, 0x53, 0x50, 0x72, 0x6f, 0x63, 
    0x53, 0x65, 0x74, 0x20, 0x31, 0x2e, 0x30, 0x20, 
    0x30, 0x0a, 0x2f, 0x65, 0x70, 0x20, 0x7b, 0x20, 
    0x25, 0x64, 0x65, 0x66, 0x0a, 0x09, 0x2f, 0x65, 
    0x70, 0x73, 0x20, 0x6d, 0x73, 0x20, 0x32, 0x20, 
    0x64, 0x69, 0x76, 0x20, 0x64, 0x65, 0x66, 0x0a, 
    0x09, 0x65, 0x78, 0x63, 0x68, 0x20, 0x33, 0x20, 
    0x69, 0x6e, 0x64, 0x65, 0x78, 0x20, 0x73, 0x75, 
    0x62, 0x0a, 0x09, 0x2f, 0x64, 0x78, 0x20, 0x65, 
    0x78, 0x63, 0x68, 0x20, 0x64, 0x65, 0x66, 0x0a, 
    0x09, 0x31, 0x20, 0x69, 0x6e, 0x64, 0x65, 0x78, 
    0x20, 0x73, 0x75, 0x62, 0x0a, 0x09, 0x2f, 0x64, 
    0x79, 0x20, 0x65, 0x78, 0x63, 0x68, 0x20, 0x64, 
    0x65, 0x66, 0x0a, 0x09, 0x64, 0x78, 0x20, 0x61, 
    0x62, 0x73, 0x20, 0x64, 0x79, 0x20, 0x61, 0x62, 
    0x73, 0x20, 0x67, 0x65, 0x20, 0x7b, 0x20, 0x25, 
    0x69, 0x66, 0x65, 0x6c, 0x73, 0x65, 0x0a, 0x09, 
    0x09, 0x2f, 0x64, 0x79, 0x20, 0x64, 0x79, 0x20, 
    0x65, 0x70, 0x73, 0x20, 0x64, 0x78, 0x20, 0x61, 
    0x62, 0x73, 0x20, 0x64, 0x69, 0x76, 0x20, 0x6d, 
    0x75, 0x6c, 0x20, 0x64, 0x65, 0x66, 0x0a, 0x09, 
    0x09, 0x2f, 0x64, 0x78, 0x20, 0x64, 0x78, 0x20, 
    0x73, 0x67, 0x6e, 0x20, 0x65, 0x70, 0x73, 0x20, 
    0x6d, 0x75, 0x6c, 0x20, 0x64, 0x65, 0x66, 0x0a, 
    0x09, 0x7d, 0x20, 0x7b, 0x20, 0x25, 0x65, 0x6c, 
    0x73, 0x65, 0x0a, 0x09, 0x09, 0x2f, 0x64, 0x78, 
    0x20, 0x64, 0x78, 0x20, 0x65, 0x70, 0x73, 0x20, 
    0x64, 0x79, 0x20, 0x61, 0x62, 0x73, 0x20, 0x64, 
    0x69, 0x76, 0x20, 0x6d, 0x75, 0x6c, 0x20, 0x64, 
    0x65, 0x66, 0x0a, 0x09, 0x09, 0x2f, 0x64, 0x79, 
    0x20, 0x64, 0x79, 0x20, 0x73, 0x67, 0x6e, 0x20, 
    0x65, 0x70, 0x73, 0x20, 0x6d, 0x75, 0x6c, 0x20, 
    0x64, 0x65, 0x66, 0x0a, 0x09, 0x7d, 0x20, 0x69, 
    0x66, 0x65, 0x6c, 0x73, 0x65, 0x0a, 0x0a, 0x09, 
    0x64, 0x79, 0x20, 0x61, 0x64, 0x64, 0x20, 0x65, 
    0x78, 0x63, 0x68, 0x20, 0x64, 0x78, 0x20, 0x61, 
    0x64, 0x64, 0x20, 0x65, 0x78, 0x63, 0x68, 0x0a, 
    0x7d, 0x20, 0x62, 0x69, 0x6e, 0x64, 0x20, 0x64, 
    0x65, 0x66, 0x0a, 0x09, 0x09, 0x0a, 0x2f, 0x61, 
    0x68, 0x20, 0x7b, 0x20, 0x25, 0x64, 0x65, 0x66, 
    0x0a, 0x09, 0x67, 0x73, 0x61, 0x76, 0x65, 0x0a, 
    0x09, 0x6d, 0x6f, 0x76, 0x65, 0x74, 0x6f, 0x0a, 
    0x09, 0x63, 0x75, 0x72, 0x72, 0x65, 0x6e, 0x74, 
    0x70, 0x6f, 0x69, 0x6e, 0x74, 0x0a, 0x09, 0x34, 
    0x20, 0x32, 0x20, 0x72, 0x6f, 0x6c, 0x6c, 0x20, 
    0x65, 0x78, 0x63, 0x68, 0x20, 0x34, 0x20, 0x2d, 
    0x31, 0x20, 0x72, 0x6f, 0x6c, 0x6c, 0x20, 0x65, 
    0x78, 0x63, 0x68, 0x0a, 0x09, 0x73, 0x75, 0x62, 
    0x20, 0x33, 0x20, 0x31, 0x20, 0x72, 0x6f, 0x6c, 
    0x6c, 0x20, 0x73, 0x75, 0x62, 0x0a, 0x09, 0x65, 
    0x78, 0x63, 0x68, 0x20, 0x61, 0x74, 0x61, 0x6e, 
    0x20, 0x72, 0x6f, 0x74, 0x61, 0x74, 0x65, 0x20, 
    0x30, 0x2e, 0x36, 0x20, 0x61, 0x64, 0x64, 0x20, 
    0x64, 0x75, 0x70, 0x20, 0x73, 0x63, 0x61, 0x6c, 
    0x65, 0x0a, 0x09, 0x2d, 0x34, 0x20, 0x30, 0x20, 
    0x72, 0x6d, 0x6f, 0x76, 0x65, 0x74, 0x6f, 0x0a, 
    0x09, 0x2d, 0x31, 0x20, 0x32, 0x20, 0x72, 0x6c, 
    0x69, 0x6e, 0x65, 0x74, 0x6f, 0x0a, 0x09, 0x37, 
    0x20, 0x2d, 0x32, 0x20, 0x72, 0x6c, 0x69, 0x6e, 
    0x65, 0x74, 0x6f, 0x0a, 0x09, 0x2d, 0x37, 0x20, 
    0x2d, 0x32, 0x20, 0x72, 0x6c, 0x69, 0x6e, 0x65, 
    0x74, 0x6f, 0x0a, 0x09, 0x63, 0x6c, 0x6f, 0x73, 
    0x65, 0x70, 0x61, 0x74, 0x68, 0x20, 0x66, 0x69, 
    0x6c, 0x6c, 0x0a, 0x09, 0x67, 0x72, 0x65, 0x73, 
    0x74, 0x6f, 0x72, 0x65, 0x0a, 0x09, 0x6e, 0x65, 
    0x77, 0x70, 0x61, 0x74, 0x68, 0x0a, 0x7d, 0x20, 
    0x62, 0x69, 0x6e, 0x64, 0x20, 0x64, 0x65, 0x66, 
    0x0a, 0x0a, 0x0a, 0x2f, 0x62, 0x6f, 0x78, 0x70, 
    0x61, 0x74, 0x68, 0x20, 0x7b, 0x6d, 0x6f, 0x76, 
    0x65, 0x74, 0x6f, 0x20, 0x64, 0x75, 0x70, 0x20, 
    0x30, 0x20, 0x72, 0x6c, 0x69, 0x6e, 0x65, 0x74, 
    0x6f, 0x20, 0x64, 0x75, 0x70, 0x20, 0x6e, 0x65, 
    0x67, 0x20, 0x30, 0x20, 0x65, 0x78, 0x63, 0x68, 
    0x20, 0x72, 0x6c, 0x69, 0x6e, 0x65, 0x74, 0x6f, 
    0x20, 0x6e, 0x65, 0x67, 0x20, 0x30, 0x20, 0x72, 
    0x6c, 0x69, 0x6e, 0x65, 0x74, 0x6f, 0x20, 0x63, 
    0x6c, 0x6f, 0x73, 0x65, 0x70, 0x61, 0x74, 0x68, 
    0x7d, 0x20, 0x62, 0x69, 0x6e, 0x64, 0x20, 0x64, 
    0x65, 0x66, 0x0a, 0x2f, 0x63, 0x69, 0x72, 0x63, 
    0x6c, 0x65, 0x70, 0x61, 0x74, 0x68, 0x20, 0x7b, 
    0x73, 0x20, 0x32, 0x20, 0x64, 0x69, 0x76, 0x20, 
    0x73, 0x75, 0x62, 0x20, 0x65, 0x78, 0x63, 0x68, 
    0x20, 0x73, 0x20, 0x32, 0x20, 0x64, 0x69, 0x76, 
    0x20, 0x61, 0x64, 0x64, 0x20, 0x65, 0x78, 0x63, 
    0x68, 0x20, 0x33, 0x20, 0x32, 0x20, 0x72, 0x6f, 
    0x6c, 0x6c, 0x20, 0x32, 0x20, 0x64, 0x69, 0x76, 
    0x20, 0x30, 0x20, 0x33, 0x36, 0x30, 0x20, 0x61, 
    0x72, 0x63, 0x7d, 0x20, 0x62, 0x69, 0x6e, 0x64, 
    0x20, 0x64, 0x65, 0x66, 0x0a, 0x0a, 0x2f, 0x43, 
    0x53, 0x31, 0x20, 0x7b, 0x6d, 0x6f, 0x76, 0x65, 
    0x74, 0x6f, 0x20, 0x64, 0x75, 0x70, 0x20, 0x73, 
    0x74, 0x72, 0x69, 0x6e, 0x67, 0x77, 0x69, 0x64, 
    0x74, 0x68, 0x20, 0x70, 0x6f, 0x70, 0x20, 0x32, 
    0x20, 0x64, 0x69, 0x76, 0x20, 0x6e, 0x65, 0x67, 
    0x20, 0x6d, 0x73, 0x20, 0x32, 0x20, 0x64, 0x69, 
    0x76, 0x20, 0x61, 0x64, 0x64, 0x20, 0x30, 0x20, 
    0x72, 0x6d, 0x6f, 0x76, 0x65, 0x74, 0x6f, 0x20, 
    0x73, 0x68, 0x6f, 0x77, 0x7d, 0x20, 0x62, 0x69, 
    0x6e, 0x64, 0x20, 0x64, 0x65, 0x66, 0x0a, 0x2f, 
    0x43, 0x53, 0x32, 0x20, 0x7b, 0x33, 0x20, 0x63, 
    0x6f, 0x70, 0x79, 0x20, 0x6d, 0x6f, 0x76, 0x65, 
    0x74, 0x6f, 0x20, 0x64, 0x75, 0x70, 0x20, 0x73, 
    0x74, 0x72, 0x69, 0x6e, 0x67, 0x77, 0x69, 0x64, 
    0x74, 0x68, 0x20, 0x70, 0x6f, 0x70, 0x20, 0x32, 
    0x20, 0x64, 0x69, 0x76, 0x20, 0x6e, 0x65, 0x67, 
    0x20, 0x6d, 0x73, 0x20, 0x32, 0x20, 0x64, 0x69, 
    0x76, 0x20, 0x61, 0x64, 0x64, 0x20, 0x31, 0x20, 
    0x73, 0x75, 0x62, 0x20, 0x2d, 0x31, 0x0a, 0x72, 
    0x6d, 0x6f, 0x76, 0x65, 0x74, 0x6f, 0x09, 0x73, 
    0x74, 0x72, 0x69, 0x6e, 0x67, 0x77, 0x69, 0x64, 
    0x74, 0x68, 0x20, 0x70, 0x6f, 0x70, 0x20, 0x66, 
    0x73, 0x20, 0x65, 0x78, 0x63, 0x68, 0x20, 0x31, 
    0x20, 0x61, 0x64, 0x64, 0x20, 0x64, 0x75, 0x70, 
    0x20, 0x30, 0x20, 0x72, 0x6c, 0x69, 0x6e, 0x65, 
    0x74, 0x6f, 0x20, 0x65, 0x78, 0x63, 0x68, 0x20, 
    0x30, 0x20, 0x65, 0x78, 0x63, 0x68, 0x20, 0x72, 
    0x6c, 0x69, 0x6e, 0x65, 0x74, 0x6f, 0x09, 0x6e, 
    0x65, 0x67, 0x20, 0x30, 0x20, 0x72, 0x6c, 0x69, 
    0x6e, 0x65, 0x74, 0x6f, 0x0a, 0x63, 0x6c, 0x6f, 
    0x73, 0x65, 0x70, 0x61, 0x74, 0x68, 0x20, 0x31, 
    0x20, 0x73, 0x65, 0x74, 0x67, 0x72, 0x61, 0x79, 
    0x20, 0x66, 0x69, 0x6c, 0x6c, 0x0a, 0x30, 0x20, 
    0x73, 0x65, 0x74, 0x67, 0x72, 0x61, 0x79, 0x20, 
    0x6d, 0x6f, 0x76, 0x65, 0x74, 0x6f, 0x20, 0x64, 
    0x75, 0x70, 0x20, 0x73, 0x74, 0x72, 0x69, 0x6e, 
    0x67, 0x77, 0x69, 0x64, 0x74, 0x68, 0x20, 0x70, 
    0x6f, 0x70, 0x20, 0x32, 0x20, 0x64, 0x69, 0x76, 
    0x20, 0x6e, 0x65, 0x67, 0x20, 0x6d, 0x73, 0x20, 
    0x32, 0x20, 0x64, 0x69, 0x76, 0x20, 0x61, 0x64, 
    0x64, 0x20, 0x30, 0x20, 0x72, 0x6d, 0x6f, 0x76, 
    0x65, 0x74, 0x6f, 0x20, 0x73, 0x68, 0x6f, 0x77, 
    0x7d, 0x20, 0x62, 0x69, 0x6e, 0x64, 0x20, 0x64, 
    0x65, 0x66, 0x0a, 0x0a, 0x0a, 0x2f, 0x43, 0x43, 
    0x53, 0x31, 0x20, 0x7b, 0x6d, 0x6f, 0x76, 0x65, 
    0x74, 0x6f, 0x20, 0x64, 0x75, 0x70, 0x20, 0x73, 
    0x74, 0x72, 0x69, 0x6e, 0x67, 0x77, 0x69, 0x64, 
    0x74, 0x68, 0x20, 0x70, 0x6f, 0x70, 0x20, 0x32, 
    0x20, 0x64, 0x69, 0x76, 0x20, 0x6e, 0x65, 0x67, 
    0x20, 0x30, 0x20, 0x72, 0x6d, 0x6f, 0x76, 0x65, 
    0x74, 0x6f, 0x20, 0x73, 0x68, 0x6f, 0x77, 0x7d, 
    0x20, 0x62, 0x69, 0x6e, 0x64, 0x20, 0x64, 0x65, 
    0x66, 0x0a, 0x2f, 0x43, 0x43, 0x53, 0x32, 0x20, 
    0x7b, 0x33, 0x20, 0x63, 0x6f, 0x70, 0x79, 0x20, 
    0x6d, 0x6f, 0x76, 0x65, 0x74, 0x6f, 0x20, 0x64, 
    0x75, 0x70, 0x20, 0x73, 0x74, 0x72, 0x69, 0x6e, 
    0x67, 0x77, 0x69, 0x64, 0x74, 0x68, 0x20, 0x70, 
    0x6f, 0x70, 0x20, 0x32, 0x20, 0x64, 0x69, 0x76, 
    0x20, 0x6e, 0x65, 0x67, 0x20, 0x30, 0x20, 0x72, 
    0x6d, 0x6f, 0x76, 0x65, 0x74, 0x6f, 0x20, 0x0a, 
    0x73, 0x74, 0x72, 0x69, 0x6e, 0x67, 0x77, 0x69, 
    0x64, 0x74, 0x68, 0x20, 0x70, 0x6f, 0x70, 0x20, 
    0x66, 0x73, 0x20, 0x65, 0x78, 0x63, 0x68, 0x20, 
    0x31, 0x20, 0x61, 0x64, 0x64, 0x20, 0x64, 0x75, 
    0x70, 0x20, 0x30, 0x20, 0x72, 0x6c, 0x69, 0x6e, 
    0x65, 0x74, 0x6f, 0x20, 0x65, 0x78, 0x63, 0x68, 
    0x20, 0x30, 0x20, 0x65, 0x78, 0x63, 0x68, 0x20, 
    0x72, 0x6c, 0x69, 0x6e, 0x65, 0x74, 0x6f, 0x09, 
    0x6e, 0x65, 0x67, 0x20, 0x30, 0x20, 0x72, 0x6c, 
    0x69, 0x6e, 0x65, 0x74, 0x6f, 0x0a, 0x63, 0x6c, 
    0x6f, 0x73, 0x65, 0x70, 0x61, 0x74, 0x68, 0x20, 
    0x31, 0x20, 0x73, 0x65, 0x74, 0x67, 0x72, 0x61, 
    0x79, 0x20, 0x66, 0x69, 0x6c, 0x6c, 0x0a, 0x30, 
    0x20, 0x73, 0x65, 0x74, 0x67, 0x72, 0x61, 0x79, 
    0x20, 0x6d, 0x6f, 0x76, 0x65, 0x74, 0x6f, 0x20, 
    0x64, 0x75, 0x70, 0x20, 0x73, 0x74, 0x72, 0x69, 
    0x6e, 0x67, 0x77, 0x69, 0x64, 0x74, 0x68, 0x20, 
    0x70, 0x6f, 0x70, 0x20, 0x32, 0x20, 0x64, 0x69, 
    0x76, 0x20, 0x6e, 0x65, 0x67, 0x20, 0x30, 0x20, 
    0x72, 0x6d, 0x6f, 0x76, 0x65, 0x74, 0x6f, 0x20, 
    0x73, 0x68, 0x6f, 0x77, 0x7d, 0x20, 0x62, 0x69, 
    0x6e, 0x64, 0x20, 0x64, 0x65, 0x66, 0x0a, 0x0a, 
    0x2f, 0x56, 0x61, 0x6c, 0x53, 0x74, 0x72, 0x20, 
    0x32, 0x30, 0x20, 0x73, 0x74, 0x72, 0x69, 0x6e, 
    0x67, 0x20, 0x64, 0x65, 0x66, 0x0a, 0x0a, 0x2f, 
    0x64, 0x75, 0x20, 0x7b, 0x0a, 0x20, 0x67, 0x73, 
    0x61, 0x76, 0x65, 0x0a, 0x20, 0x20, 0x2f, 0x73, 
    0x20, 0x65, 0x78, 0x63, 0x68, 0x20, 0x64, 0x75, 
    0x70, 0x20, 0x30, 0x20, 0x65, 0x71, 0x20, 0x6e, 
    0x6f, 0x74, 0x20, 0x7b, 0x75, 0x6c, 0x77, 0x20, 
    0x73, 0x75, 0x62, 0x7d, 0x20, 0x69, 0x66, 0x20, 
    0x64, 0x65, 0x66, 0x20, 0x0a, 0x20, 0x20, 0x2f, 
    0x79, 0x20, 0x65, 0x78, 0x63, 0x68, 0x20, 0x75, 
    0x6c, 0x79, 0x20, 0x73, 0x75, 0x62, 0x20, 0x72, 
    0x73, 0x20, 0x6d, 0x75, 0x6c, 0x20, 0x66, 0x73, 
    0x20, 0x32, 0x20, 0x64, 0x69, 0x76, 0x20, 0x61, 
    0x64, 0x64, 0x20, 0x6e, 0x65, 0x67, 0x20, 0x64, 
    0x65, 0x66, 0x20, 0x0a, 0x20, 0x20, 0x2f, 0x78, 
    0x20, 0x65, 0x78, 0x63, 0x68, 0x20, 0x75, 0x6c, 
    0x78, 0x20, 0x73, 0x75, 0x62, 0x20, 0x72, 0x73, 
    0x20, 0x6d, 0x75, 0x6c, 0x20, 0x64, 0x65, 0x66, 
    0x0a, 0x20, 0x20, 0x6e, 0x65, 0x77, 0x70, 0x61, 
    0x74, 0x68, 0x20, 0x73, 0x20, 0x30, 0x20, 0x65, 
    0x71, 0x20, 0x7b, 0x70, 0x6f, 0x70, 0x20, 0x70, 
    0x6f, 0x70, 0x20, 0x70, 0x6f, 0x70, 0x7d, 0x20, 
    0x7b, 0x20, 0x0a, 0x20, 0x20, 0x20, 0x20, 0x20, 
    0x20, 0x20, 0x73, 0x20, 0x78, 0x20, 0x6d, 0x73, 
    0x20, 0x73, 0x20, 0x73, 0x75, 0x62, 0x20, 0x32, 
    0x20, 0x64, 0x69, 0x76, 0x20, 0x61, 0x64, 0x64, 
    0x20, 0x79, 0x20, 0x6d, 0x73, 0x20, 0x73, 0x20, 
    0x73, 0x75, 0x62, 0x20, 0x32, 0x20, 0x64, 0x69, 
    0x76, 0x20, 0x73, 0x75, 0x62, 0x20, 0x0a, 0x20, 
    0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x75, 0x70, 
    0x20, 0x67, 0x73, 0x61, 0x76, 0x65, 0x20, 0x73, 
    0x65, 0x74, 0x72, 0x67, 0x62, 0x63, 0x6f, 0x6c, 
    0x6f, 0x72, 0x20, 0x66, 0x69, 0x6c, 0x6c, 0x20, 
    0x67, 0x72, 0x65, 0x73, 0x74, 0x6f, 0x72, 0x65, 
    0x0a, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 
    0x30, 0x20, 0x73, 0x65, 0x74, 0x67, 0x72, 0x61, 
    0x79, 0x20, 0x75, 0x6c, 0x77, 0x20, 0x73, 0x65, 
    0x74, 0x6c, 0x69, 0x6e, 0x65, 0x77, 0x69, 0x64, 
    0x74, 0x68, 0x20, 0x73, 0x74, 0x72, 0x6f, 0x6b, 
    0x65, 0x20, 0x7d, 0x20, 0x69, 0x66, 0x65, 0x6c, 
    0x73, 0x65, 0x0a, 0x20, 0x20, 0x74, 0x65, 0x72, 
    0x20, 0x74, 0x65, 0x62, 0x20, 0x74, 0x65, 0x67, 
    0x20, 0x73, 0x65, 0x74, 0x72, 0x67, 0x62, 0x63, 
    0x6f, 0x6c, 0x6f, 0x72, 0x0a, 0x20, 0x20, 0x73, 
    0x68, 0x62, 0x20, 0x7b, 0x78, 0x20, 0x79, 0x20, 
    0x6d, 0x73, 0x20, 0x73, 0x75, 0x62, 0x20, 0x66, 
    0x73, 0x20, 0x73, 0x75, 0x62, 0x20, 0x43, 0x53, 
    0x7d, 0x7b, 0x70, 0x6f, 0x70, 0x7d, 0x20, 0x69, 
    0x66, 0x65, 0x6c, 0x73, 0x65, 0x0a, 0x20, 0x20, 
    0x73, 0x68, 0x74, 0x20, 0x7b, 0x78, 0x20, 0x79, 
    0x20, 0x31, 0x2e, 0x35, 0x20, 0x61, 0x64, 0x64, 
    0x20, 0x43, 0x53, 0x7d, 0x7b, 0x70, 0x6f, 0x70, 
    0x7d, 0x20, 0x69, 0x66, 0x65, 0x6c, 0x73, 0x65, 
    0x0a, 0x20, 0x67, 0x72, 0x65, 0x73, 0x74, 0x6f, 
    0x72, 0x65, 0x0a, 0x7d, 0x20, 0x62, 0x69, 0x6e, 
    0x64, 0x20, 0x64, 0x65, 0x66, 0x0a, 0x0a, 0x2f, 
    0x63, 0x6f, 0x20, 0x7b, 0x0a, 0x20, 0x67, 0x73, 
    0x61, 0x76, 0x65, 0x0a, 0x20, 0x20, 0x73, 0x65, 
    0x74, 0x72, 0x67, 0x62, 0x63, 0x6f, 0x6c, 0x6f, 
    0x72, 0x0a, 0x20, 0x20, 0x2f, 0x74, 0x79, 0x20, 
    0x65, 0x78, 0x63, 0x68, 0x20, 0x75, 0x6c, 0x79, 
    0x20, 0x73, 0x75, 0x62, 0x20, 0x72, 0x73, 0x20, 
    0x6d, 0x75, 0x6c, 0x20, 0x6e, 0x65, 0x67, 0x20, 
    0x6d, 0x73, 0x20, 0x32, 0x20, 0x64, 0x69, 0x76, 
    0x20, 0x73, 0x75, 0x62, 0x20, 0x64, 0x65, 0x66, 
    0x0a, 0x20, 0x20, 0x2f, 0x74, 0x78, 0x20, 0x65, 
    0x78, 0x63, 0x68, 0x20, 0x75, 0x6c, 0x78, 0x20, 
    0x73, 0x75, 0x62, 0x20, 0x72, 0x73, 0x20, 0x6d, 
    0x75, 0x6c, 0x20, 0x6d, 0x73, 0x20, 0x32, 0x20, 
    0x64, 0x69, 0x76, 0x20, 0x61, 0x64, 0x64, 0x20, 
    0x64, 0x65, 0x66, 0x0a, 0x20, 0x20, 0x2f, 0x73, 
    0x79, 0x20, 0x65, 0x78, 0x63, 0x68, 0x20, 0x75, 
    0x6c, 0x79, 0x20, 0x73, 0x75, 0x62, 0x20, 0x72, 
    0x73, 0x20, 0x6d, 0x75, 0x6c, 0x20, 0x6e, 0x65, 
    0x67, 0x20, 0x6d, 0x73, 0x20, 0x32, 0x20, 0x64, 
    0x69, 0x76, 0x20, 0x73, 0x75, 0x62, 0x20, 0x64, 
    0x65, 0x66, 0x0a, 0x20, 0x20, 0x2f, 0x73, 0x78, 
    0x20, 0x65, 0x78, 0x63, 0x68, 0x20, 0x75, 0x6c, 
    0x78, 0x20, 0x73, 0x75, 0x62, 0x20, 0x72, 0x73, 
    0x20, 0x6d, 0x75, 0x6c, 0x20, 0x6d, 0x73, 0x20, 
    0x32, 0x20, 0x64, 0x69, 0x76, 0x20, 0x61, 0x64, 
    0x64, 0x20, 0x64, 0x65, 0x66, 0x0a, 0x20, 0x20, 
    0x73, 0x78, 0x20, 0x73, 0x79, 0x20, 0x74, 0x78, 
    0x20, 0x74, 0x79, 0x20, 0x65, 0x70, 0x20, 0x74, 
    0x78, 0x20, 0x74, 0x79, 0x20, 0x73, 0x78, 0x20, 
    0x73, 0x79, 0x20, 0x65, 0x70, 0x0a, 0x20, 0x20, 
    0x2f, 0x74, 0x79, 0x20, 0x65, 0x78, 0x63, 0x68, 
    0x20, 0x64, 0x65, 0x66, 0x20, 0x2f, 0x74, 0x78, 
    0x20, 0x65, 0x78, 0x63, 0x68, 0x20, 0x64, 0x65, 
    0x66, 0x20, 0x2f, 0x73, 0x79, 0x20, 0x65, 0x78, 
    0x63, 0x68, 0x20, 0x64, 0x65, 0x66, 0x20, 0x2f, 
    0x73, 0x78, 0x20, 0x65, 0x78, 0x63, 0x68, 0x20, 
    0x64, 0x65, 0x66, 0x20, 0x73, 0x78, 0x20, 0x73, 
    0x79, 0x20, 0x6d, 0x6f, 0x76, 0x65, 0x74, 0x6f, 
    0x20, 0x74, 0x78, 0x20, 0x74, 0x79, 0x20, 0x6c, 
    0x69, 0x6e, 0x65, 0x74, 0x6f, 0x0a, 0x20, 0x20, 
    0x6c, 0x77, 0x20, 0x73, 0x65, 0x74, 0x6c, 0x69, 
    0x6e, 0x65, 0x77, 0x69, 0x64, 0x74, 0x68, 0x20, 
    0x73, 0x74, 0x72, 0x6f, 0x6b, 0x65, 0x0a, 0x20, 
    0x20, 0x64, 0x69, 0x20, 0x7b, 0x6c, 0x77, 0x20, 
    0x73, 0x78, 0x20, 0x73, 0x79, 0x20, 0x74, 0x78, 
    0x20, 0x74, 0x79, 0x20, 0x61, 0x68, 0x7d, 0x20, 
    0x69, 0x66, 0x0a, 0x20, 0x20, 0x73, 0x77, 0x20, 
    0x7b, 0x0a, 0x09, 0x74, 0x65, 0x72, 0x20, 0x74, 
    0x65, 0x62, 0x20, 0x74, 0x65, 0x67, 0x20, 0x73, 
    0x65, 0x74, 0x72, 0x67, 0x62, 0x63, 0x6f, 0x6c, 
    0x6f, 0x72, 0x0a, 0x09, 0x74, 0x78, 0x20, 0x73, 
    0x78, 0x20, 0x61, 0x64, 0x64, 0x20, 0x32, 0x20, 
    0x64, 0x69, 0x76, 0x09, 0x74, 0x79, 0x20, 0x73, 
    0x79, 0x20, 0x61, 0x64, 0x64, 0x20, 0x32, 0x20, 
    0x64, 0x69, 0x76, 0x0a, 0x09, 0x74, 0x79, 0x20, 
    0x73, 0x79, 0x20, 0x73, 0x75, 0x62, 0x20, 0x30, 
    0x20, 0x65, 0x71, 0x20, 0x7b, 0x66, 0x73, 0x20, 
    0x31, 0x20, 0x61, 0x64, 0x64, 0x20, 0x32, 0x20, 
    0x64, 0x69, 0x76, 0x20, 0x74, 0x78, 0x20, 0x73, 
    0x78, 0x20, 0x73, 0x75, 0x62, 0x20, 0x73, 0x67, 
    0x6e, 0x20, 0x6e, 0x65, 0x67, 0x20, 0x6d, 0x75, 
    0x6c, 0x20, 0x61, 0x64, 0x64, 0x7d, 0x7b, 0x66, 
    0x73, 0x20, 0x31, 0x20, 0x61, 0x64, 0x64, 0x20, 
    0x32, 0x20, 0x64, 0x69, 0x76, 0x20, 0x74, 0x79, 
    0x20, 0x73, 0x79, 0x20, 0x73, 0x75, 0x62, 0x20, 
    0x73, 0x67, 0x6e, 0x20, 0x6e, 0x65, 0x67, 0x20, 
    0x6d, 0x75, 0x6c, 0x20, 0x61, 0x64, 0x64, 0x7d, 
    0x20, 0x69, 0x66, 0x65, 0x6c, 0x73, 0x65, 0x0a, 
    0x09, 0x66, 0x73, 0x20, 0x31, 0x20, 0x61, 0x64, 
    0x64, 0x20, 0x32, 0x20, 0x64, 0x69, 0x76, 0x20, 
    0x73, 0x75, 0x62, 0x20, 0x43, 0x43, 0x53, 0x0a, 
    0x20, 0x20, 0x7d, 0x20, 0x7b, 0x70, 0x6f, 0x70, 
    0x7d, 0x20, 0x69, 0x66, 0x65, 0x6c, 0x73, 0x65, 
    0x0a, 0x20, 0x67, 0x72, 0x65, 0x73, 0x74, 0x6f, 
    0x72, 0x65, 0x0a, 0x7d, 0x20, 0x62, 0x69, 0x6e, 
    0x64, 0x20, 0x64, 0x65, 0x66, 0x0a, 0x0a, 0x25, 
    0x25, 0x45, 0x6e, 0x64, 0x50, 0x72, 0x6f, 0x63, 
    0x53, 0x65, 0x74, 0x0a, 0x25, 0x25, 0x45, 0x6e, 
    0x64, 0x50, 0x72, 0x6f, 0x6c, 0x6f, 0x67, 0x0a, 
    0x0a, 0x25, 0x25, 0x42, 0x65, 0x67, 0x69, 0x6e, 
    0x50, 0x61, 0x67, 0x65, 0x0a, 0x25, 0x25, 0x50, 
    0x61, 0x67, 0x65, 0x3a, 0x20, 0x31, 0x20, 0x31, 
    0x0a, 0x0a, 0x25, 0x67, 0x65, 0x74, 0x20, 0x66, 
    0x6f, 0x6e, 0x74, 0x20, 0x0a, 0x20, 0x2f, 0x66, 
    0x73, 0x20, 0x75, 0x66, 0x73, 0x20, 0x64, 0x65, 
    0x66, 0x20, 0x66, 0x6f, 0x20, 0x66, 0x69, 0x6e, 
    0x64, 0x66, 0x6f, 0x6e, 0x74, 0x20, 0x66, 0x73, 
    0x20, 0x73, 0x63, 0x61, 0x6c, 0x65, 0x66, 0x6f, 
    0x6e, 0x74, 0x20, 0x73, 0x65, 0x74, 0x66, 0x6f, 
    0x6e, 0x74, 0x0a, 0x0a, 0x20, 0x20, 0x73, 0x63, 
    0x20, 0x7b, 0x2f, 0x75, 0x70, 0x20, 0x7b, 0x63, 
    0x69, 0x72, 0x63, 0x6c, 0x65, 0x70, 0x61, 0x74, 
    0x68, 0x7d, 0x20, 0x62, 0x69, 0x6e, 0x64, 0x20, 
    0x64, 0x65, 0x66, 0x7d, 0x7b, 0x2f, 0x75, 0x70, 
    0x20, 0x7b, 0x62, 0x6f, 0x78, 0x70, 0x61, 0x74, 
    0x68, 0x7d, 0x20, 0x62, 0x69, 0x6e, 0x64, 0x20, 
    0x64, 0x65, 0x66, 0x7d, 0x20, 0x69, 0x66, 0x65, 
    0x6c, 0x73, 0x65, 0x0a, 0x20, 0x20, 0x74, 0x74, 
    0x20, 0x7b, 0x2f, 0x43, 0x53, 0x20, 0x7b, 0x43, 
    0x53, 0x31, 0x7d, 0x20, 0x62, 0x69, 0x6e, 0x64, 
    0x20, 0x64, 0x65, 0x66, 0x7d, 0x7b, 0x2f, 0x43, 
    0x53, 0x20, 0x7b, 0x43, 0x53, 0x32, 0x7d, 0x20, 
    0x62, 0x69, 0x6e, 0x64, 0x20, 0x64, 0x65, 0x66, 
    0x7d, 0x20, 0x69, 0x66, 0x65, 0x6c, 0x73, 0x65, 
    0x0a, 0x20, 0x20, 0x74, 0x74, 0x20, 0x7b, 0x2f, 
    0x43, 0x43, 0x53, 0x20, 0x7b, 0x43, 0x43, 0x53, 
    0x31, 0x7d, 0x20, 0x62, 0x69, 0x6e, 0x64, 0x20, 
    0x64, 0x65, 0x66, 0x7d, 0x7b, 0x2f, 0x43, 0x43, 
    0x53, 0x20, 0x7b, 0x43, 0x43, 0x53, 0x32, 0x7d, 
    0x20, 0x62, 0x69, 0x6e, 0x64, 0x20, 0x64, 0x65, 
    0x66, 0x7d, 0x20, 0x69, 0x66, 0x65, 0x6c, 0x73, 
    0x65, 0x0a, 0x0a, 0x25, 0x25, 0x42, 0x65, 0x67, 
    0x69, 0x6e, 0x50, 0x61, 0x67, 0x65, 0x53, 0x65, 
    0x74, 0x75, 0x70, 0x0a, 0x25, 0x63, 0x6f, 0x6e, 
    0x73, 0x69, 0x64, 0x65, 0x72, 0x20, 0x62, 0x6f, 
    0x72, 0x64, 0x65, 0x72, 0x0a, 0x20, 0x4c, 0x61, 
    0x6e, 0x64, 0x73, 0x63, 0x61, 0x70, 0x65, 0x4d, 
    0x6f, 0x64, 0x65, 0x20, 0x7b, 0x39, 0x30, 0x20, 
    0x72, 0x6f, 0x74, 0x61, 0x74, 0x65, 0x20, 0x62, 
    0x68, 0x20, 0x62, 0x76, 0x20, 0x6e, 0x65, 0x67, 
    0x20, 0x74, 0x72, 0x61, 0x6e, 0x73, 0x6c, 0x61, 
    0x74, 0x65, 0x7d, 0x7b, 0x62, 0x68, 0x20, 0x70, 
    0x68, 0x20, 0x62, 0x76, 0x20, 0x61, 0x64, 0x64, 
    0x20, 0x74, 0x72, 0x61, 0x6e, 0x73, 0x6c, 0x61, 
    0x74, 0x65, 0x7d, 0x20, 0x69, 0x66, 0x65, 0x6c, 
    0x73, 0x65, 0x0a, 0x0a, 0x25, 0x63, 0x6c, 0x69, 
    0x70, 0x70, 0x61, 0x74, 0x68, 0x0a, 0x20, 0x20, 
    0x6e, 0x65, 0x77, 0x70, 0x61, 0x74, 0x68, 0x0a, 
    0x20, 0x20, 0x73, 0x62, 0x20, 0x7b, 0x67, 0x73, 
    0x61, 0x76, 0x65, 0x0a, 0x09, 0x62, 0x6c, 0x77, 
    0x20, 0x32, 0x20, 0x64, 0x69, 0x76, 0x20, 0x6e, 
    0x65, 0x67, 0x20, 0x62, 0x6c, 0x77, 0x20, 0x32, 
    0x20, 0x64, 0x69, 0x76, 0x20, 0x6d, 0x6f, 0x76, 
    0x65, 0x74, 0x6f, 0x20, 0x30, 0x20, 0x70, 0x68, 
    0x20, 0x62, 0x6c, 0x77, 0x20, 0x61, 0x64, 0x64, 
    0x20, 0x6e, 0x65, 0x67, 0x20, 0x72, 0x6c, 0x69, 
    0x6e, 0x65, 0x74, 0x6f, 0x20, 0x70, 0x77, 0x20, 
    0x62, 0x6c, 0x77, 0x0a, 0x20, 0x20, 0x20, 0x20, 
    0x20, 0x20, 0x20, 0x20, 0x61, 0x64, 0x64, 0x20, 
    0x30, 0x20, 0x72, 0x6c, 0x69, 0x6e, 0x65, 0x74, 
    0x6f, 0x20, 0x30, 0x20, 0x70, 0x68, 0x20, 0x62, 
    0x6c, 0x77, 0x20, 0x61, 0x64, 0x64, 0x20, 0x72, 
    0x6c, 0x69, 0x6e, 0x65, 0x74, 0x6f, 0x20, 0x63, 
    0x6c, 0x6f, 0x73, 0x65, 0x70, 0x61, 0x74, 0x68, 
    0x0a, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 
    0x20, 0x30, 0x20, 0x73, 0x65, 0x74, 0x67, 0x72, 
    0x61, 0x79, 0x20, 0x62, 0x6c, 0x77, 0x20, 0x73, 
    0x65, 0x74, 0x6c, 0x69, 0x6e, 0x65, 0x77, 0x69, 
    0x64, 0x74, 0x68, 0x20, 0x73, 0x74, 0x72, 0x6f, 
    0x6b, 0x65, 0x20, 0x0a, 0x20, 0x20, 0x20, 0x20, 
    0x20, 0x20, 0x67, 0x72, 0x65, 0x73, 0x74, 0x6f, 
    0x72, 0x65, 0x7d, 0x20, 0x69, 0x66, 0x0a, 0x20, 
    0x20, 0x30, 0x20, 0x30, 0x20, 0x6d, 0x6f, 0x76, 
    0x65, 0x74, 0x6f, 0x20, 0x30, 0x20, 0x70, 0x68, 
    0x20, 0x6e, 0x65, 0x67, 0x20, 0x72, 0x6c, 0x69, 
    0x6e, 0x65, 0x74, 0x6f, 0x20, 0x70, 0x77, 0x20, 
    0x30, 0x20, 0x72, 0x6c, 0x69, 0x6e, 0x65, 0x74, 
    0x6f, 0x20, 0x30, 0x20, 0x70, 0x68, 0x20, 0x72, 
    0x6c, 0x69, 0x6e, 0x65, 0x74, 0x6f, 0x20, 0x63, 
    0x6c, 0x6f, 0x73, 0x65, 0x70, 0x61, 0x74, 0x68, 
    0x0a, 0x20, 0x20, 0x63, 0x61, 0x62, 0x20, 0x7b, 
    0x63, 0x6c, 0x69, 0x70, 0x7d, 0x20, 0x69, 0x66, 
    0x0a, 0x20, 0x20, 0x67, 0x73, 0x61, 0x76, 0x65, 
    0x20, 0x63, 0x6c, 0x69, 0x70, 0x70, 0x61, 0x74, 
    0x68, 0x20, 0x62, 0x67, 0x72, 0x20, 0x62, 0x67, 
    0x67, 0x20, 0x62, 0x67, 0x62, 0x20, 0x73, 0x65, 
    0x74, 0x72, 0x67, 0x62, 0x63, 0x6f, 0x6c, 0x6f, 
    0x72, 0x20, 0x66, 0x69, 0x6c, 0x6c, 0x20, 0x67, 
    0x72, 0x65, 0x73, 0x74, 0x6f, 0x72, 0x65, 0x0a, 
    0x0a, 0x0a, 0x0a, 0x25, 0x66, 0x69, 0x6e, 0x61, 
    0x6c, 0x20, 0x61, 0x64, 0x6a, 0x75, 0x73, 0x74, 
    0x6d, 0x65, 0x6e, 0x74, 0x20, 0x74, 0x6f, 0x20, 
    0x75, 0x73, 0x65, 0x72, 0x20, 0x63, 0x6f, 0x6f, 
    0x72, 0x64, 0x69, 0x6e, 0x61, 0x74, 0x65, 0x20, 
    0x73, 0x70, 0x61, 0x63, 0x65, 0x0a, 0x20, 0x20, 
    0x30, 0x20, 0x70, 0x77, 0x20, 0x69, 0x77, 0x20, 
    0x69, 0x73, 0x78, 0x20, 0x6d, 0x75, 0x6c, 0x20, 
    0x73, 0x75, 0x62, 0x20, 0x6d, 0x61, 0x78, 0x20, 
    0x32, 0x20, 0x64, 0x69, 0x76, 0x20, 0x66, 0x73, 
    0x20, 0x6e, 0x65, 0x67, 0x20, 0x30, 0x20, 0x70, 
    0x68, 0x20, 0x69, 0x68, 0x20, 0x69, 0x73, 0x79, 
    0x20, 0x6d, 0x75, 0x6c, 0x20, 0x73, 0x75, 0x62, 
    0x20, 0x6d, 0x61, 0x78, 0x20, 0x32, 0x20, 0x64, 
    0x69, 0x76, 0x20, 0x73, 0x75, 0x62, 0x0a, 0x20, 
    0x20, 0x74, 0x72, 0x61, 0x6e, 0x73, 0x6c, 0x61, 
    0x74, 0x65, 0x20, 0x69, 0x73, 0x78, 0x20, 0x69, 
    0x73, 0x79, 0x20, 0x73, 0x63, 0x61, 0x6c, 0x65, 
    0x0a, 0x25, 0x25, 0x45, 0x6e, 0x64, 0x50, 0x61, 
    0x67, 0x65, 0x53, 0x65, 0x74, 0x75, 0x70, 0x0a, 
    0x0a, 0x00
};
#endif /* HEADER_COMPILE */
/* end private definition section */
#endif /* _UI_PRINT_DEFINED_ */
/* end of file */
/* lines: 23 */
