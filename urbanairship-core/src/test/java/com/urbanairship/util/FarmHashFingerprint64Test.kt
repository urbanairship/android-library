package com.urbanairship.util

import com.google.common.base.Strings
import com.urbanairship.BaseTestCase
import org.junit.Assert
import org.junit.Test

internal class FarmHashFingerprint64Test : BaseTestCase() {

    private val testData = mapOf(
    "dXB@tDQ-v5<H]rq2Pcc*s>nC-[Mdy" to 8365906589669344754UL,
    "!@#$%^&*():=-_][\\|/?.,<> " to 11772040268694734364UL,
    "&&3gRU?[^&ok:He[|K:" to 11792583603419566171UL,
    "9JqLl0AW7e69Y.&vMHQ5C" to 2827089714349584095UL,
    "F7479877-4690-4A44-AFC9-8FE987EA512F:some_other_id" to 6862335115798125349UL,
    "hg[F|\$D&hb$,V4OeXHOa" to 11873385450325105043UL,
    "/dWQW6&i7h$1@" to 11452602314494946942UL,
    "2/?98ns)xbzEVL^:wCS$7l3@_g!zP^<D.-bd6" to 9728733090894310797UL,
    "?c^6BkI#-SLw" to 13133570674398037786UL,
    "wE,gHSvhK Jv=KR#(R |!%vctTJ0fx)" to 413905253809041649UL,
    "5C \$WnO2K@:(4#h" to 2463546464490189UL,
    "Ijiq13Mb_Nn]sA^jhM7eZ\\ExAzSJ" to 12345582939087509209UL,
    ")D<l91" to 6440615040757739207UL,
    "mC=6Tz,AYH|&n99(G!6LyG&QfZ=1^:" to 10432240328043398052UL,
    "7.b^/n=oR_w(vLN?c?xN<5t\$p8HY2!s:U" to 2506644862971557451UL,
    "t,SRdW>l=?AH4\\JQ!.A)Wh,O4\\8" to 4614517891525318442UL,
    "K6Pjv<>ad" to 16506019169567922731UL,
    "" to 11160318154034397263UL,
    "Q" to 13816109650407654260UL,
    "bF&d\$MYIhB.Ac=qC" to 17582099205024456557UL,
    "#cDR^sLO" to 328147020574120176UL,
    "NXooOPwHej5=c_V0(47=-)N!vNdd:\$fMs1B" to 5510670329273533446UL,
    "y2=B@rsu:g9bWU" to 2493222351070148393UL,
    "wi=%v]GoIPI6zm[Rrgmq]7J?.|" to 8222623341199956836UL,
    "Sl,xx&O^l@=TQ[QI(TJ^aD*PS3.K]@Mk:e)e" to 12943788826636368730UL,
    "@05Mz\\\\)VhZ\\S&9vVU,egF%sW)IMIGVHE%#I)D|" to 134798502762145076UL,
    "e#p8" to 252499459662674074UL,
    ">EtzDE,xUUZ%!aCvx#vyN(][Q.eRQO2sBZCwFH" to 5047171257402399197UL,
    "ECCD828C-5D7A-4C8B-9A1B-F244747E96C3" to 9693850515132481856UL,
    "D<wQ1DVVpS" to 876767294656777789UL,
    ",=" to 1326014594494455617UL,
    "EsIIjI65<^!j$)V.,!]M]@Q5[$(oyxI_nF" to 4212098974972931626UL,
    "fDVY|(%&aF#3<l>b?1Y Hqt)qY(0%b@VIk#Rlofs" to 1687730506231245221UL,
    "^b2z)XYJ\\95" to 3150268469206098298UL,
    "9>Nleb)=|CR#4=G2&7[HOP" to 10511875379468936029UL,
    "M)(iJ1-nf>5XCc0L?" to 9968500208262240300UL,
    "WW5" to 6316074107149058620UL,
    "ZyzWj:&3hH78.WUCNW4e&Z " to 13218358187761524434UL,
    "P9|0-Xg" to 15614415556471156694UL,
    "n?(o|a[EX|KN-9./=tCVEmN%?<MXe8F<" to 1754206644017466002UL,
    "&QEO\\" to 673322083973757863UL,
    "T#e:),mqALpU]hrJ%f.*|=&r" to 11789374840096016445UL,
    "xi\\PvQpHpM:$5\\Zh^U" to 4169389423472268625UL,
    "!/tU|0cMaw=/-Yg)m_*4UNvwB" to 14890523890635468863UL
    )

    @Test
    fun testKnownOutput() {
        testData.forEach { (key, value) ->
            Assert.assertEquals(
                value.toLong(),
                FarmHashFingerprint64.fingerprint(key)
            )
        }
    }

    @Test
    fun testReallySimpleFingerprints() {
        Assert.assertEquals(8581389452482819506L, FarmHashFingerprint64.fingerprint("test"))
        // 32 characters long
        Assert.assertEquals(
            -4196240717365766262L,
            FarmHashFingerprint64.fingerprint(Strings.repeat("test", 8))
        )
        // 256 characters long
        Assert.assertEquals(
            3500507768004279527L,
            FarmHashFingerprint64.fingerprint(Strings.repeat("test", 64))
        )
    }
}
