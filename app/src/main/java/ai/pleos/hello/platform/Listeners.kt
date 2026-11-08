package ai.pleos.hello.platform

/** SdkHub 의 직렬 실행 경로(D3)로 작업을 넘기는 함수. 모든 SDK 콜백은 이걸 거쳐 리스너에 도달한다. */
typealias Post = (() -> Unit) -> Unit

/** 직렬 경로 안에서만 쓰는 단순 리스너 목록. */
class Listeners<T> {
    private val items = ArrayList<T>()
    fun add(l: T) { if (l !in items) items.add(l) }
    fun remove(l: T) { items.remove(l) }
    fun forEach(f: (T) -> Unit) = items.toList().forEach(f)
}
