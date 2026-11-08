package ai.pleos.hello.proto

import ai.pleos.hello.proto.kairy.r1a
import ai.pleos.hello.proto.kairy.r1b
import ai.pleos.hello.proto.kairy.r1c
import ai.pleos.hello.proto.kairy.r1d
import ai.pleos.hello.proto.kairy.r1e
import ai.pleos.hello.proto.pleorycard.cardA
import ai.pleos.hello.proto.pleorycard.cardB
import ai.pleos.hello.proto.pleorycard.cardC
import ai.pleos.hello.proto.pleorycard.s0a
import ai.pleos.hello.proto.pleorycard.s0b
import ai.pleos.hello.proto.pleorycard.r2.R2Index

object ProtoIndex {
    fun register() {
        Protos.all["kairy/r1/a"] = ::r1a
        Protos.all["kairy/r1/b"] = ::r1b
        Protos.all["kairy/r1/c"] = ::r1c
        Protos.all["kairy/r1/d"] = ::r1d
        Protos.all["kairy/r1/e"] = ::r1e

        Protos.all["pleorycard/r1/a"] = ::cardA
        Protos.all["pleorycard/r1/b"] = ::cardB
        Protos.all["pleorycard/r1/c"] = ::cardC
        Protos.all["pleorycard/r1/s0a"] = { a -> s0a(a) }
        Protos.all["pleorycard/r1/s0a-listen"] = { a -> s0a(a, listening = true) }
        Protos.all["pleorycard/r1/s0b"] = { a -> s0b(a) }
        Protos.all["pleorycard/r1/s0b-listen"] = { a -> s0b(a, listening = true) }

        R2Index.register(Protos.all)
    }
}
