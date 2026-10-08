package app.reporove.core.reader

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** App-owned script: untrusted fragments are JSON data, never executable source. */
object ReaderAnchors {
    fun script(fragment: String): String {
        val value = Json.encodeToString(fragment.removePrefix("#"))
        return """
            (() => {
              const id = $value;
              if (!id) { window.scrollTo(0, 0); return true; }
              const plain = id.replace(/^user-content-/, '');
              let target = document.getElementById(id) || document.getElementById(plain)
                || document.getElementById('user-content-' + plain)
                || document.getElementsByName(id)[0] || document.getElementsByName(plain)[0];
              if (!target) return false;
              if (target.matches('a.anchor')) {
                const parent = target.parentElement;
                target = parent.matches('h1,h2,h3,h4,h5,h6') ? parent
                  : parent.querySelector('h1,h2,h3,h4,h5,h6') || parent;
              }
              for (let node = target.parentElement; node; node = node.parentElement) {
                if (node.tagName === 'DETAILS') node.open = true;
              }
              target.scrollIntoView({block:'start'});
              return true;
            })()
        """.trimIndent()
    }
}
