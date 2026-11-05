import { addEventListener, each, extend } from "./utils/general-utils.mjs";
import { DEFAULT_AUTOCAPTURE_IGNORE_LIST, MAX_DOM_ANCESTOR_DEPTH, autocaptureCompatibleElements, elementMatchesCSSSelector, getClassNames, getDirectAndNestedSpanText, getElementsChainString, getEventTarget, getSafeText, isAngularStyleAttr, isSensitiveElement, makeSafeText, shouldCaptureDomEvent, shouldCaptureElement, shouldCaptureRageclick, shouldCaptureValue, splitClassString } from "./utils/autocapture-utils.mjs";
import rageclick from "./rageclick.mjs";
import { AUTOCAPTURE_DISABLED_SERVER_SIDE } from "./constants.mjs";
import { includes, isArray, isBoolean, isFunction, isNull, stripUrlHash } from "@posthog/core";
import { createLogger } from "./utils/logger.mjs";
import { convertToURL } from "./utils/convert-to-url.mjs";
import { isElementNode, isShadowRoot, isTag, isTextNode } from "./utils/element-utils.mjs";
const getWindow = ()=>"u" < typeof window ? void 0 : window;
const getDocument = ()=>"u" < typeof document ? void 0 : document;
const COPY_AUTOCAPTURE_EVENT = '$copy_autocapture';
const EXCLUDED_INPUT_TYPES = [
    'password',
    'hidden',
    'file',
    'checkbox',
    'button',
    'submit',
    'reset',
    'image'
];
function isValueInputElement(element) {
    if (isTag(element, 'input')) return !includes(EXCLUDED_INPUT_TYPES, element.type);
    return isTag(element, 'textarea') || isTag(element, 'select') && !element.multiple;
}
function isInputValueEligible(target, config) {
    if (!shouldCaptureElement(target)) return false;
    const ignorelist = config.css_selector_ignorelist ?? DEFAULT_AUTOCAPTURE_IGNORE_LIST;
    const seen = new Set();
    let element = target;
    if (isTag(target, 'select')) {
        const select = target;
        element = select.options[select.selectedIndex] || target;
    }
    while(element){
        const current = element;
        const classes = getClassNames(current);
        if (seen.size >= MAX_DOM_ANCESTOR_DEPTH || seen.has(element) || includes(classes, 'ph-sensitive') || includes(classes, 'ph-no-capture') || ignorelist.some((selector)=>elementMatchesCSSSelector(current, selector))) return false;
        seen.add(element);
        const parent = element.assignedSlot || element.parentNode;
        element = isShadowRoot(parent) ? parent.host : parent && isElementNode(parent) ? parent : null;
    }
    return true;
}
const logger = createLogger('[AutoCapture]');
function limitText(length, text) {
    if (text.length > length) return text.slice(0, length) + '...';
    return text;
}
function getAugmentPropertiesFromElement(elem) {
    const shouldCaptureEl = shouldCaptureElement(elem);
    if (!shouldCaptureEl) return {};
    const props = {};
    each(elem.attributes, function(attr) {
        if (attr.name && 0 === attr.name.indexOf('data-ph-capture-attribute')) {
            const propertyKey = attr.name.replace('data-ph-capture-attribute-', '');
            const propertyValue = attr.value;
            if (propertyKey && propertyValue && shouldCaptureValue(propertyValue)) props[propertyKey] = propertyValue;
        }
    });
    return props;
}
function previousElementSibling(el) {
    if (el.previousElementSibling) return el.previousElementSibling;
    let _el = el;
    do _el = _el.previousSibling;
    while (_el && !isElementNode(_el));
    return _el;
}
function getDefaultProperties(eventType) {
    return {
        $event_type: eventType,
        $ce_version: 1
    };
}
function getPropertiesFromElement(elem, maskAllAttributes, maskText, elementAttributeIgnorelist, disableCaptureUrlHashes = false) {
    const tag_name = elem.tagName.toLowerCase();
    const props = {
        tag_name: tag_name
    };
    if (autocaptureCompatibleElements.indexOf(tag_name) > -1 && !maskText) if ('a' === tag_name.toLowerCase() || 'button' === tag_name.toLowerCase()) props['$el_text'] = limitText(1024, getDirectAndNestedSpanText(elem));
    else props['$el_text'] = limitText(1024, getSafeText(elem));
    const classes = getClassNames(elem);
    if (!maskAllAttributes && !elementAttributeIgnorelist?.includes('class') && classes.length > 0) props['classes'] = classes.filter(function(c) {
        return '' !== c;
    });
    each(elem.attributes, function(attr) {
        if (isSensitiveElement(elem) && -1 === [
            'name',
            'id',
            'class',
            'aria-label'
        ].indexOf(attr.name)) return;
        if (elementAttributeIgnorelist?.includes(attr.name)) return;
        if (!maskAllAttributes && shouldCaptureValue(attr.value) && !isAngularStyleAttr(attr.name)) {
            let value = attr.value;
            if ('class' === attr.name) value = splitClassString(value).join(' ');
            props['attr__' + attr.name] = limitText(1024, 'href' === attr.name && disableCaptureUrlHashes ? stripUrlHash(value) : value);
        }
    });
    let nthChild = 1;
    let nthOfType = 1;
    let currentElem = elem;
    while(currentElem = previousElementSibling(currentElem)){
        nthChild++;
        if (currentElem.tagName === elem.tagName) nthOfType++;
    }
    props['nth_child'] = nthChild;
    props['nth_of_type'] = nthOfType;
    return props;
}
function autocapturePropertiesForElement(target, { e, maskAllElementAttributes, maskAllText, elementAttributeIgnoreList, elementsChainAsString, disableCaptureUrlHashes }) {
    if (!isElementNode(target)) return {
        props: {}
    };
    const targetElementList = [
        target
    ];
    const seen = new Set([
        target
    ]);
    let curEl = target;
    while(curEl.parentNode && !isTag(curEl, 'body')){
        if (targetElementList.length >= MAX_DOM_ANCESTOR_DEPTH) break;
        if (isShadowRoot(curEl.parentNode)) {
            const host = curEl.parentNode.host;
            if (seen.has(host)) break;
            seen.add(host);
            targetElementList.push(host);
            curEl = host;
            continue;
        }
        if (!isElementNode(curEl.parentNode)) break;
        if (seen.has(curEl.parentNode)) break;
        seen.add(curEl.parentNode);
        targetElementList.push(curEl.parentNode);
        curEl = curEl.parentNode;
    }
    const controlIndex = 'click' === e.type && 'http://www.w3.org/2000/svg' === target.namespaceURI ? targetElementList.findIndex((el)=>isTag(el, 'button') || isTag(el, 'a')) : -1;
    if (controlIndex > 0) maskAllText = maskAllText || targetElementList.slice(0, controlIndex + 1).some((el)=>!shouldCaptureElement(el) || isSensitiveElement(el));
    const elementsJson = [];
    const autocaptureAugmentProperties = {};
    let href = false;
    let explicitNoCapture = false;
    each(targetElementList, (el)=>{
        const shouldCaptureEl = shouldCaptureElement(el);
        if (isTag(el, 'a') && !maskAllElementAttributes && !elementAttributeIgnoreList?.includes('href')) {
            const hrefAttr = el.getAttribute('href');
            href = shouldCaptureEl && !!hrefAttr && shouldCaptureValue(hrefAttr) ? disableCaptureUrlHashes ? stripUrlHash(hrefAttr) : hrefAttr : false;
        }
        const classes = getClassNames(el);
        if (includes(classes, 'ph-no-capture')) explicitNoCapture = true;
        elementsJson.push(getPropertiesFromElement(el, maskAllElementAttributes, maskAllText, elementAttributeIgnoreList, disableCaptureUrlHashes));
        const augmentProperties = getAugmentPropertiesFromElement(el);
        each(augmentProperties, (value, key)=>{
            if (!Object.prototype.hasOwnProperty.call(autocaptureAugmentProperties, key)) autocaptureAugmentProperties[key] = value;
        });
    });
    if (explicitNoCapture) return {
        props: {},
        explicitNoCapture
    };
    if (controlIndex > 0) {
        target = targetElementList[controlIndex];
        elementsJson.splice(0, controlIndex);
    }
    if (!maskAllText) if (isTag(target, 'a') || isTag(target, 'button')) elementsJson[0]['$el_text'] = getDirectAndNestedSpanText(target);
    else elementsJson[0]['$el_text'] = getSafeText(target);
    let externalHref;
    if (href) {
        elementsJson[0]['attr__href'] = href;
        const hrefHost = convertToURL(href)?.host;
        const locationHost = getWindow()?.location?.host;
        if (hrefHost && locationHost && hrefHost !== locationHost) externalHref = href;
    }
    const props = extend(getDefaultProperties(e.type), elementsChainAsString ? {} : {
        $elements: elementsJson
    }, {
        $elements_chain: getElementsChainString(elementsJson)
    }, elementsJson[0]?.['$el_text'] ? {
        $el_text: elementsJson[0]?.['$el_text']
    } : {}, externalHref && 'click' === e.type ? {
        $external_click_url: externalHref
    } : {}, autocaptureAugmentProperties);
    return {
        props,
        target
    };
}
const AutocaptureExtension = 'autocapture';
class Autocapture {
    _clearPointer() {
        this._pointerDown = void 0;
        clearTimeout(this._pointerExpiry);
        this._pointerExpiry = void 0;
    }
    _isExcludedPointerTarget(target) {
        if (!shouldCaptureElement(target)) return true;
        const ignorelist = this._refreshConfig().css_selector_ignorelist ?? DEFAULT_AUTOCAPTURE_IGNORE_LIST;
        const seen = new Set();
        let el = target;
        while(el){
            if (seen.size >= MAX_DOM_ANCESTOR_DEPTH || seen.has(el)) return true;
            seen.add(el);
            const current = el;
            const classes = getClassNames(current);
            if (includes(classes, 'ph-no-capture') || includes(classes, 'ph-sensitive') || isSensitiveElement(current) || ignorelist.some((selector)=>elementMatchesCSSSelector(current, selector))) return true;
            const parent = el.parentNode;
            el = isShadowRoot(parent) ? parent.host : parent && isElementNode(parent) ? parent : null;
        }
        return false;
    }
    _trackPointer(e) {
        if (!this.isEnabled || 'pointercancel' === e.type) return void this._clearPointer();
        if ('pointerdown' === e.type) {
            this._clearPointer();
            const target = getEventTarget(e);
            if (e.isPrimary && 0 === e.button && !e.ctrlKey && target && isElementNode(target)) this._pointerDown = {
                target,
                excluded: this._isExcludedPointerTarget(target),
                id: e.pointerId,
                x: e.clientX,
                y: e.clientY,
                released: false,
                distinctId: this._client?.distinctId
            };
            return;
        }
        const down = this._pointerDown;
        if (!down) return;
        if (e.pointerId !== down.id || Math.abs(e.clientX - down.x) > 5 || Math.abs(e.clientY - down.y) > 5) this._clearPointer();
        else if ('pointerup' === e.type) {
            down.released = true;
            this._pointerExpiry = setTimeout(()=>this._clearPointer(), 0);
        }
    }
    _recoverClickTarget(e) {
        const down = this._pointerDown;
        this._clearPointer();
        const target = getEventTarget(e);
        if (down?.released && down.target.isConnected && down.distinctId === this._client?.distinctId && e.pointerId === down.id && (e.detail > 0 || 'touch' === e.pointerType || 'pen' === e.pointerType) && 0 === e.button && Math.abs(e.clientX - down.x) <= 5 && Math.abs(e.clientY - down.y) <= 5 && target && (isTag(target, 'html') || isTag(target, 'body'))) {
            if (down.excluded) return null;
            const ignorelist = this._refreshConfig().css_selector_ignorelist ?? DEFAULT_AUTOCAPTURE_IGNORE_LIST;
            const excludedRoot = [
                target,
                getDocument()?.documentElement,
                getDocument()?.body
            ].some((root)=>{
                if (!root) return false;
                const classes = getClassNames(root);
                return includes(classes, 'ph-no-capture') || includes(classes, 'ph-sensitive') || !shouldCaptureElement(root) || isSensitiveElement(root) || ignorelist.some((selector)=>elementMatchesCSSSelector(root, selector));
            });
            if (excludedRoot) return null;
            return down.target;
        }
    }
    constructor(_configSource){
        this._configSource = _configSource;
        this.name = AutocaptureExtension;
        this._initialized = false;
        this._isDisabledServerSide = null;
        this._hasReceivedConfigResponse = false;
        this._elementSelectorsByOwner = new Map();
        this._elementsChainAsString = false;
        this._config = {
            enabled: false,
            rageclick: false,
            maskAllElementAttributes: false,
            maskAllText: false,
            disableCaptureUrlHashes: false,
            remoteRequestsDisabled: false
        };
        this._disposed = false;
        this._configSource.refresh(this._config);
        this.rageclicks = new rageclick(this._config.rageclick);
        this._elementSelectors = null;
    }
    setup(client) {
        this._compileUrlPatterns();
        this._client = client;
        const subscription = client.onRemoteConfig(this.onRemoteConfig.bind(this));
        if (this._disposed) return void subscription.dispose();
        this._remoteConfigSubscription = subscription;
        this.startIfEnabled();
    }
    dispose() {
        if (this._disposed) return;
        this._disposed = true;
        this._client = void 0;
        this._remoteConfigSubscription?.dispose();
        this._remoteConfigSubscription = void 0;
        this._elementSelectorsByOwner.clear();
        this._elementSelectors = null;
        this._removeDomEventHandlers();
    }
    _refreshConfig() {
        this._configSource.refresh(this._config);
        return this._config;
    }
    _compileUrlPatterns() {
        this._refreshConfig();
        this._config.url_allowlist = this._config.url_allowlist?.map((url)=>new RegExp(url));
        this._config.url_ignorelist = this._config.url_ignorelist?.map((url)=>new RegExp(url));
        return this._config;
    }
    _addDomEventHandlers() {
        const window1 = getWindow();
        const document1 = getDocument();
        if (!this.isBrowserSupported()) return void logger.info('Disabling Automatic Event Collection because this browser is not supported');
        if (!window1 || !document1) return;
        this._listenerWindow = window1;
        this._listenerDocument = document1;
        const handler = this._domEventHandler = (e)=>{
            e = e || window1?.event;
            try {
                if ('blur' === e.type) return void this._clearPointer();
                if (0 === e.type.indexOf('pointer')) return void this._trackPointer(e);
                const target = 'click' === e.type ? this._recoverClickTarget(e) : void 0;
                if (isNull(target)) return;
                this._captureEvent(e, '$autocapture', target);
            } catch (error) {
                logger.error('Failed to capture event', error);
            }
        };
        addEventListener(window1, 'blur', handler);
        addEventListener(document1, 'submit', handler, {
            capture: true
        });
        addEventListener(document1, 'change', handler, {
            capture: true
        });
        addEventListener(document1, 'click', handler, {
            capture: true
        });
        each([
            'pointerdown',
            'pointermove',
            'pointerup',
            'pointercancel'
        ], (type)=>{
            addEventListener(document1, type, handler, {
                capture: true
            });
        });
        if (this._refreshConfig().capture_copied_text) {
            const copiedTextHandler = this._copiedTextHandler = (e)=>{
                e = e || window1?.event;
                try {
                    this._captureEvent(e, COPY_AUTOCAPTURE_EVENT);
                } catch (error) {
                    logger.error('Failed to capture clipboard event', error);
                }
            };
            addEventListener(document1, 'copy', copiedTextHandler, {
                capture: true
            });
            addEventListener(document1, 'cut', copiedTextHandler, {
                capture: true
            });
            addEventListener(document1, 'paste', copiedTextHandler, {
                capture: true
            });
        }
    }
    _removeDomEventHandlers() {
        const window1 = this._listenerWindow;
        const document1 = this._listenerDocument;
        this._clearPointer();
        if (this._domEventHandler) {
            window1?.removeEventListener('blur', this._domEventHandler);
            each([
                'pointerdown',
                'pointermove',
                'pointerup',
                'pointercancel'
            ], (type)=>{
                document1?.removeEventListener(type, this._domEventHandler, true);
            });
            document1?.removeEventListener('submit', this._domEventHandler, true);
            document1?.removeEventListener('change', this._domEventHandler, true);
            document1?.removeEventListener('click', this._domEventHandler, true);
            this._domEventHandler = void 0;
        }
        if (this._copiedTextHandler) {
            document1?.removeEventListener('copy', this._copiedTextHandler, true);
            document1?.removeEventListener('cut', this._copiedTextHandler, true);
            document1?.removeEventListener('paste', this._copiedTextHandler, true);
            this._copiedTextHandler = void 0;
        }
        this._listenerWindow = void 0;
        this._listenerDocument = void 0;
        this._initialized = false;
    }
    startIfEnabled() {
        if (!this.isEnabled) this._clearPointer();
        if (!this._disposed && this._client && this.isEnabled && !this._initialized) {
            this._addDomEventHandlers();
            this._initialized = true;
        }
    }
    onRemoteConfig(result) {
        if (this._disposed) return;
        this._hasReceivedConfigResponse = true;
        if (!result.ok) return void this.startIfEnabled();
        const response = result.config;
        if (response.elementsChainAsString) this._elementsChainAsString = response.elementsChainAsString;
        const optOut = response['autocapture_opt_out'];
        if (isBoolean(optOut)) {
            this._client?.kv.set(AUTOCAPTURE_DISABLED_SERVER_SIDE, optOut);
            this._isDisabledServerSide = optOut;
        }
        this.startIfEnabled();
    }
    setElementSelectors(selectors, owner = this) {
        if (this._disposed) return;
        if (selectors.size) this._elementSelectorsByOwner.set(owner, new Set(selectors));
        else this._elementSelectorsByOwner.delete(owner);
        const combined = new Set();
        this._elementSelectorsByOwner.forEach((contribution)=>{
            contribution.forEach((selector)=>combined.add(selector));
        });
        this._elementSelectors = combined;
    }
    getElementSelectors(element, additionalElement) {
        const elementSelectors = [];
        this._elementSelectors?.forEach((selector)=>{
            const matchedElements = getDocument()?.querySelectorAll(selector);
            matchedElements?.forEach((matchedElement)=>{
                if ((element === matchedElement || additionalElement === matchedElement) && !includes(elementSelectors, selector)) elementSelectors.push(selector);
            });
        });
        return elementSelectors;
    }
    get isEnabled() {
        if (this._disposed) return false;
        const persistedServerDisabled = this._client?.kv.get(AUTOCAPTURE_DISABLED_SERVER_SIDE);
        const memoryDisabled = this._isDisabledServerSide;
        const config = this._refreshConfig();
        if (!config.remoteRequestsDisabled && !this._hasReceivedConfigResponse) return false;
        const clientConfigOnly = config.remoteRequestsDisabled && !this._hasReceivedConfigResponse;
        if (isNull(memoryDisabled) && !isBoolean(persistedServerDisabled) && !clientConfigOnly) return false;
        const disabledServer = this._isDisabledServerSide ?? !!persistedServerDisabled;
        const disabledClient = !config.enabled;
        return !disabledClient && !disabledServer;
    }
    _captureEvent(e, eventName = '$autocapture', targetOverride) {
        if (!this.isEnabled) return void this._clearPointer();
        let target = targetOverride || getEventTarget(e);
        if (isTextNode(target)) target = target.parentNode || null;
        const config = this._compileUrlPatterns();
        if ('$autocapture' === eventName && 'click' === e.type && e instanceof MouseEvent) {
            if (!!config.rageclick && this.rageclicks?.isRageClick(e.clientX, e.clientY, e.timeStamp || new Date().getTime())) {
                if (shouldCaptureRageclick(target, config.rageclick)) this._captureEvent(e, '$rageclick', targetOverride);
            }
        }
        const isClipboardAutocapture = eventName === COPY_AUTOCAPTURE_EVENT;
        const eventConfig = isClipboardAutocapture ? {
            ...config,
            dom_event_allowlist: void 0
        } : config;
        if (target && shouldCaptureDomEvent(target, e, eventConfig, isClipboardAutocapture, isClipboardAutocapture ? [
            'copy',
            'cut',
            'paste'
        ] : void 0, {
            config: {
                get_current_url: config.getCurrentUrl
            }
        })) {
            const { props, explicitNoCapture, target: attributedTarget } = autocapturePropertiesForElement(target, {
                e,
                maskAllElementAttributes: config.maskAllElementAttributes,
                maskAllText: config.maskAllText,
                elementAttributeIgnoreList: config.element_attribute_ignorelist,
                elementsChainAsString: this._elementsChainAsString,
                disableCaptureUrlHashes: config.disableCaptureUrlHashes
            });
            if (explicitNoCapture) return false;
            const elementSelectors = this.getElementSelectors(target, attributedTarget);
            if (elementSelectors && elementSelectors.length > 0) props['$element_selectors'] = elementSelectors;
            if (eventName === COPY_AUTOCAPTURE_EVENT) {
                const clipType = e.type || 'clipboard';
                if ('paste' !== clipType) {
                    const selectedText = getWindow()?.getSelection()?.toString();
                    const selectedContent = makeSafeText(selectedText);
                    if (!selectedContent) return false;
                    props['$selected_content'] = selectedContent;
                    props['$clipboard_text_length'] = selectedText?.length ?? 0;
                }
                props['$copy_type'] = clipType;
            }
            if ('$autocapture' === eventName && 'change' === e.type && config.capture_value_css_selector_allowlist && !config.maskAllText && isValueInputElement(target) && isInputValueEligible(target, config) && !target.querySelector('.ph-no-capture, .ph-sensitive')) {
                const allowlist = config.capture_value_css_selector_allowlist;
                let captureValue = false;
                try {
                    captureValue = isFunction(allowlist) ? !!allowlist(target) : isArray(allowlist) && allowlist.some((selector)=>elementMatchesCSSSelector(target, selector));
                } catch (error) {
                    logger.error('capture_value_css_selector_allowlist threw', error);
                }
                if (captureValue) props['$input_value'] = target.value;
            }
            this._client?.capture(eventName, props);
            return true;
        }
    }
    isBrowserSupported() {
        return isFunction(getDocument()?.querySelectorAll);
    }
}
export { Autocapture, AutocaptureExtension, autocapturePropertiesForElement, getAugmentPropertiesFromElement, getDefaultProperties, getPropertiesFromElement, previousElementSibling };
