/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#include "org_jitsi_impl_neomedia_jmfext_media_protocol_directshow_DSFormat.h"

#include <windows.h>
#include <dshow.h>
#include <uuids.h>

#define DEFINE_DSFORMAT_PIXELFORMAT(pixFmt) \
    JNIEXPORT jint JNICALL \
    Java_org_jitsi_impl_neomedia_jmfext_media_protocol_directshow_DSFormat_##pixFmt \
        (JNIEnv *env, jclass clazz) \
    { \
        return MEDIASUBTYPE_##pixFmt.Data1; \
    }

DEFINE_DSFORMAT_PIXELFORMAT(ARGB32)
DEFINE_DSFORMAT_PIXELFORMAT(AYUV)
DEFINE_DSFORMAT_PIXELFORMAT(I420)
DEFINE_DSFORMAT_PIXELFORMAT(IF09)
DEFINE_DSFORMAT_PIXELFORMAT(IMC1)
DEFINE_DSFORMAT_PIXELFORMAT(IMC2)
DEFINE_DSFORMAT_PIXELFORMAT(IMC3)
DEFINE_DSFORMAT_PIXELFORMAT(IMC4)
DEFINE_DSFORMAT_PIXELFORMAT(IYUV)
DEFINE_DSFORMAT_PIXELFORMAT(NV12)
DEFINE_DSFORMAT_PIXELFORMAT(RGB24)
DEFINE_DSFORMAT_PIXELFORMAT(RGB32)
DEFINE_DSFORMAT_PIXELFORMAT(UYVY)
DEFINE_DSFORMAT_PIXELFORMAT(Y211)
DEFINE_DSFORMAT_PIXELFORMAT(Y411)
DEFINE_DSFORMAT_PIXELFORMAT(Y41P)
DEFINE_DSFORMAT_PIXELFORMAT(YUY2)
DEFINE_DSFORMAT_PIXELFORMAT(YV12)
DEFINE_DSFORMAT_PIXELFORMAT(YVU9)
DEFINE_DSFORMAT_PIXELFORMAT(YVYU)
DEFINE_DSFORMAT_PIXELFORMAT(MJPG)