package com.aicontrol.launcher.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View

object UiTheme {
    val bg=Color.rgb(8,10,18)
    val card=Color.rgb(20,23,35)
    val card2=Color.rgb(27,29,46)
    val accent=Color.rgb(70,210,255)
    val accent2=Color.rgb(155,92,255)
    val textPrimary=Color.WHITE
    val textMuted=Color.rgb(170,178,198)
    val success=Color.rgb(75,220,145)
    val warning=Color.rgb(255,190,75)
    val danger=Color.rgb(255,88,105)
    fun rounded(color:Int,radiusDp:Float,strokeColor:Int?=null,strokeDp:Int=0)=GradientDrawable().apply{setColor(color);cornerRadius=radiusDp;if(strokeColor!=null&&strokeDp>0)setStroke(strokeDp,strokeColor)}
    fun gradient(radiusDp:Float=24f)=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(accent2,accent)).apply{cornerRadius=radiusDp}
    fun styleCard(view:View,color:Int=card,stroke:Boolean=false){view.background=rounded(color,24f,if(stroke)accent else null,if(stroke)1 else 0);view.elevation=6f}
}
