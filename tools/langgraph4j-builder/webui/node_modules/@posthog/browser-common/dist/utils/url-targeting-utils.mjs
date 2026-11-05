import { isFunction, isString, isUndefined } from "@posthog/core";
import { logger } from "./logger.mjs";
function applyUrlTargetingOverride(instance, defaultUrl) {
    const override = instance?.config?.get_current_url;
    if (!isFunction(override)) return defaultUrl;
    try {
        const result = override(defaultUrl);
        return isString(result) && result ? result : defaultUrl;
    } catch (e) {
        logger.error('Error in get_current_url, falling back to window.location.href', e);
        return defaultUrl;
    }
}
function getTargetingUrl(instance) {
    const win = "u" < typeof window ? void 0 : window;
    const defaultUrl = win?.location?.href;
    return isUndefined(defaultUrl) ? void 0 : applyUrlTargetingOverride(instance, defaultUrl);
}
export { applyUrlTargetingOverride, getTargetingUrl };
