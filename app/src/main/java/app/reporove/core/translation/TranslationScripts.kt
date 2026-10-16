package app.reporove.core.translation

import kotlinx.serialization.json.JsonPrimitive

/** No native JS bridge: only app-owned evaluations with JSON-encoded arguments. */
object TranslationScripts {
    private fun quote(text: String) = JsonPrimitive(text).toString()
    val visible = """(() => Array.from(document.querySelectorAll('[data-rr-source]')).map(e=>({id:e.dataset.rrSource,r:e.getBoundingClientRect()})).filter(x=>x.r.bottom>0&&x.r.top<innerHeight*1.7&&x.r.height>0).sort((a,b)=>Math.abs(a.r.top)-Math.abs(b.r.top)).slice(0,8).map(x=>x.id))()"""
    fun mode(enabled: Boolean) = """(() => {const a=Array.from(document.querySelectorAll('[data-rr-source]')).find(e=>e.getBoundingClientRect().bottom>0);const y=a?.getBoundingClientRect().top;document.body.classList.toggle('rr-bilingual',$enabled);if(a)window.scrollBy(0,a.getBoundingClientRect().top-y);})()"""
    fun insert(id: String, html: String, error: Boolean = false) = """(() => {
        const source=document.querySelector('[data-rr-source='+${quote(id)}+']'); if(!source)return;
        const anchor=Array.from(document.querySelectorAll('[data-rr-source]')).find(e=>e.getBoundingClientRect().bottom>0);
        const y=anchor?.getBoundingClientRect().top;
        let output=document.querySelector('[data-rr-target='+${quote(id)}+']');
        if(!output){output=document.createElement('span');output.dataset.rrTarget=${quote(id)};source.after(output);}
        output.className='rr-translation'+${quote(if (error) " rr-error" else "")};output.innerHTML=${quote(html)};
        document.querySelectorAll('[data-scroll-kind]').forEach(e=>{const over=String(e.scrollWidth>e.clientWidth+1);e.dataset.overflow=over;e.previousElementSibling?.setAttribute('data-overflow',over);});
        if(anchor)window.scrollBy(0,anchor.getBoundingClientRect().top-y);
    })()""".trimIndent()
    fun error(id: String, message: String): String {
        val safe = org.jsoup.nodes.TextNode(message).outerHtml()
        return insert(id, "$safe <a href=\"https://reader.reporove.invalid/translate-retry/$id\">重试</a>", true)
    }
}
