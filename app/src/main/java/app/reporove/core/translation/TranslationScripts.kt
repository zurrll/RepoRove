package app.reporove.core.translation

import kotlinx.serialization.json.JsonPrimitive

/** Only app-owned scripts, with JSON arguments and a document-generation guard. */
object TranslationScripts {
    private fun quote(text: String) = JsonPrimitive(text).toString()
    fun inDocument(token: String, script: String) = """(() => {if(document.body?.dataset.rrDocument!==${quote(token)})return null;return ($script);})()"""
    val visible = """(() => {
        const stamp=[scrollY,innerHeight,innerWidth,window.__rrLayoutVersion||0].join(':');
        if(window.__rrViewportStamp===stamp)return window.__rrViewportIds;
        const ids=Array.from(document.querySelectorAll('[data-rr-source]')).map(e=>{
        const r=e.getBoundingClientRect(),t=e.nextElementSibling?.dataset.rrTarget===e.dataset.rrSource?e.nextElementSibling.getBoundingClientRect():r;
        return {id:e.dataset.rrSource,top:Math.min(r.top,t.top),bottom:Math.max(r.bottom,t.bottom),height:r.height};
    }).filter(x=>x.bottom>-innerHeight*.3&&x.top<innerHeight*3&&x.height>0)
    .sort((a,b)=>((a.bottom>0&&a.top<innerHeight)?0:1)-((b.bottom>0&&b.top<innerHeight)?0:1)||Math.max(0,a.top)-Math.max(0,b.top))
    .slice(0,24).map(x=>x.id);
        window.__rrViewportStamp=stamp;window.__rrViewportIds=ids;return ids;
    })()"""
    val reset = """(() => {window.__rrPending?.clear();document.querySelectorAll('[data-rr-target]').forEach(e=>e.remove());})()"""
    fun mode(enabled: Boolean) = """(() => {
        if(!window.__rrListening){
            window.__rrListening=true;
            if(window.ResizeObserver){window.__rrObserver=new ResizeObserver(()=>{window.__rrLayoutVersion=(window.__rrLayoutVersion||0)+1;});window.__rrObserver.observe(document.body);}
            document.addEventListener('touchstart',()=>{window.__rrTouching=true;window.__rrTouchUntil=Date.now()+2000;},{passive:true});
            document.addEventListener('touchmove',()=>{window.__rrTouchUntil=Date.now()+2000;},{passive:true});
            document.addEventListener('touchend',()=>{window.__rrTouching=false;window.__rrBusyUntil=Date.now()+200;},{passive:true});
            document.addEventListener('touchcancel',()=>{window.__rrTouching=false;window.__rrBusyUntil=Date.now()+200;},{passive:true});
            window.addEventListener('scroll',()=>{if(!window.__rrAdjusting)window.__rrBusyUntil=Date.now()+180;},{passive:true});
        }
        document.body.classList.toggle('rr-bilingual',$enabled);
    })()"""
    fun insert(id: String, html: String, error: Boolean = false, complete: Boolean = false) = """(() => {
        const generation=document.body.dataset.rrDocument;
        const pending=window.__rrPending||(window.__rrPending=new Map());
        pending.set(${quote(id)},{html:${quote(html)},error:$error,complete:$complete});
        if(window.__rrScheduled)return;
        window.__rrScheduled=true;
        function flush(){
            if(document.body?.dataset.rrDocument!==generation)return;
            if((window.__rrTouching&&Date.now()<(window.__rrTouchUntil||0))||Date.now()<(window.__rrBusyUntil||0)){setTimeout(flush,80);return;}
            const anchor=Array.from(document.querySelectorAll('[data-rr-source]')).find(e=>e.getBoundingClientRect().bottom>0);
            const y=anchor?.getBoundingClientRect().top,changed=new Set();
            pending.forEach((value,key)=>{
                const source=document.querySelector('[data-rr-source="'+key+'"]');if(!source)return;
                let target=source.nextElementSibling;
                if(target?.dataset.rrTarget!==key){target=document.createElement('span');target.dataset.rrTarget=key;source.after(target);}
                target.className='rr-translation'+(value.error?' rr-error':'');target.dataset.rrComplete=String(value.complete);target.innerHTML=value.html;
                const scroller=target.closest('[data-scroll-kind]');if(scroller)changed.add(scroller);
            });
            pending.clear();window.__rrScheduled=false;window.__rrLayoutVersion=(window.__rrLayoutVersion||0)+1;
            changed.forEach(e=>{const over=String(e.scrollWidth>e.clientWidth+1);e.dataset.overflow=over;e.previousElementSibling?.setAttribute('data-overflow',over);});
            if(anchor){const delta=anchor.getBoundingClientRect().top-y;if(Math.abs(delta)>1){window.__rrAdjusting=true;window.scrollBy(0,delta);requestAnimationFrame(()=>{window.__rrAdjusting=false;});}}
        }
        requestAnimationFrame(flush);
    })()""".trimIndent()
    fun error(id: String, message: String, retryable: Boolean = true): String {
        val safe = org.jsoup.nodes.TextNode(message).outerHtml()
        val retry = if (retryable) " <a href=\"https://reader.reporove.invalid/translate-retry/$id\">重试</a>" else ""
        return insert(id, safe + retry, true)
    }
}
