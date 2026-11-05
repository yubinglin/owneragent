import { detectDeviceType } from "@posthog/core";
import { propertyComparisons } from "@posthog/core/surveys";
function doesDeviceTypeMatch(deviceTypes, matchType) {
    if (!deviceTypes || 0 === deviceTypes.length) return true;
    const win = "u" > typeof window ? window : void 0;
    const global = "u" > typeof globalThis ? globalThis : win;
    const navigator = global?.navigator;
    const userAgent = navigator?.userAgent;
    if (!userAgent) return false;
    const deviceType = detectDeviceType(userAgent, {
        userAgentDataPlatform: navigator?.userAgentData?.platform,
        maxTouchPoints: navigator?.maxTouchPoints,
        screenWidth: win?.screen?.width,
        screenHeight: win?.screen?.height,
        devicePixelRatio: win?.devicePixelRatio
    });
    return propertyComparisons[matchType ?? 'icontains'](deviceTypes, [
        deviceType
    ]);
}
function hasPeriodPassed(periodDays, lastSeenDate) {
    if (!periodDays || !lastSeenDate) return true;
    const date = 'string' == typeof lastSeenDate ? new Date(lastSeenDate) : lastSeenDate;
    const now = new Date();
    const diffMs = Math.abs(now.getTime() - date.getTime());
    const diffDays = Math.ceil(diffMs / 86400000);
    return diffDays > periodDays;
}
export { doesDeviceTypeMatch, hasPeriodPassed };
