package ai.pleos.hello.proto

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.TextView

/** 시안 레지스트리. 키 = "<pkg>/r<N>/<letter>" */
object Protos {
    val all: MutableMap<String, (Activity) -> View> = linkedMapOf()
}

class ProtoActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProtoIndex.register()
        val id = intent.getStringExtra("id")
        val build = Protos.all[id]
        setContentView(build?.invoke(this) ?: TextView(this).apply {
            textSize = 24f
            text = "없는 시안: $id\n\n" + Protos.all.keys.joinToString("\n")
        })
    }
}
