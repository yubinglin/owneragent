import { isUndefined } from "@posthog/core";
const convertToURL = (url)=>{
    const doc = "u" < typeof document ? void 0 : document;
    const location = doc?.createElement('a');
    if (isUndefined(location)) return null;
    location.href = url;
    return location;
};
export { convertToURL };
