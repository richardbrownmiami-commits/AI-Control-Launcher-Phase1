package com.aicontrol.launcher.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import com.aicontrol.launcher.actions.ActionEngine

object UiTheme {
    var bg=Color.rgb(8,10,18); private set
    var card=Color.rgb(20,23,35); private set
    var card2=Color.rgb(27,29,46); private set
    var accent=Color.rgb(70,210,255); private set
    var accent2=Color.rgb(155,92,255); private set
    val textPrimary=Color.WHITE
    val textMuted=Color.rgb(170,178,198)
    val success=Color.rgb(75,220,145)
    val warning=Color.rgb(255,190,75)
    val danger=Color.rgb(255,88,105)

    fun bind(engine:ActionEngine){
        val t=engine.themeState()
        bg=t.bg;card=t.card;card2=blend(t.card,t.bg,0.35f);accent=t.accent;accent2=t.accent2
    }
    private fun blend(a:Int,b:Int,f:Float):Int=Color.rgb(
        (Color.red(a)*(1-f)+Color.red(b)*f).toInt(),
        (Color.green(a)*(1-f)+Color.green(b)*f).toInt(),
        (Color.blue(a)*(1-f)+Color.blue(b)*f).toInt()
    )
    fun rounded(color:Int,radiusDp:Float,strokeColor:Int?=null,strokeDp:Int=0)=GradientDrawable().apply{
        setColor(color);cornerRadius=radiusDp
        if(strokeColor!=null&&strokeDp>0)setStroke(strokeDp,strokeColor)
    }
    fun gradient(radiusDp:Float=24f)=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(accent2,accent)).apply{cornerRadius=radiusDp}
    fun background()=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(bg,blend(bg,accent2,0.82f))).apply{cornerRadius=0f}
    fun styleCard(view:View,color:Int=card,stroke:Boolean=false){
        view.background=rounded(color,24f,if(stroke)accent else null,if(stroke)1 else 0);view.elevation=6f
    }
}