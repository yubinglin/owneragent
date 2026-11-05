import { isFunction } from "@posthog/core";
const noop = ()=>{};
const spliceableLayer = (method)=>{
    if (!isFunction(method)) return;
    return method.__posthog_layer__ ?? method.__rrweb_layer__;
};
function patch(source, name, replacement) {
    try {
        if (!(name in source)) return noop;
        const original = source[name];
        const layer = {
            next: original
        };
        const callNext = function(...args) {
            return layer.next.apply(this, args);
        };
        const wrapped = replacement(callNext);
        if (isFunction(wrapped)) {
            wrapped.prototype = wrapped.prototype || {};
            Object.defineProperties(wrapped, {
                __posthog_wrapped__: {
                    enumerable: false,
                    value: true
                },
                __posthog_layer__: {
                    enumerable: false,
                    value: layer
                }
            });
        }
        source[name] = wrapped;
        return ()=>{
            if (source[name] === wrapped) {
                source[name] = layer.next;
                return;
            }
            let current = source[name];
            let currentLayer = spliceableLayer(current);
            while(currentLayer){
                if (currentLayer.next === wrapped) {
                    currentLayer.next = layer.next;
                    return;
                }
                current = currentLayer.next;
                currentLayer = spliceableLayer(current);
            }
        };
    } catch  {
        return noop;
    }
}
export { patch };
