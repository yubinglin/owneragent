import { isArray, isError, isFile, isUndefined, safeJsonStringify } from "@posthog/core";
import { each, errorToProperties } from "./general-utils.mjs";
import { location as external_globals_mjs_location } from "./globals.mjs";
import { logger } from "./logger.mjs";
const localDomains = [
    'localhost',
    '127.0.0.1'
];
const jsonStringify = (data, space)=>{
    try {
        let errors;
        return JSON.stringify(data, (_, value)=>{
            if (isError(value)) {
                errors ??= new WeakMap();
                if (!errors.has(value)) errors.set(value, errorToProperties(value));
                return errors.get(value);
            }
            return 'bigint' == typeof value ? value.toString() : value;
        }, space);
    } catch  {
        return safeJsonStringify(data);
    }
};
const formDataToQuery = function(formdata, arg_separator = '&') {
    let use_val;
    let use_key;
    const tph_arr = [];
    each(formdata, function(val, key) {
        if (isUndefined(val) || isUndefined(key) || 'undefined' === key) return;
        use_val = encodeURIComponent(isFile(val) ? val.name : val.toString());
        use_key = encodeURIComponent(key);
        tph_arr[tph_arr.length] = use_key + '=' + use_val;
    });
    return tph_arr.join(arg_separator);
};
const getQueryParam = function(url, param) {
    const withoutHash = url.split('#')[0] || '';
    const queryParams = withoutHash.split(/\?(.*)/)[1] || '';
    const cleanedQueryParams = queryParams.replace(/^\?+/g, '');
    const queryParts = cleanedQueryParams.split('&');
    let keyValuePair;
    for(let i = 0; i < queryParts.length; i++){
        const parts = queryParts[i].split(/=(.*)/);
        if (parts[0] === param) {
            keyValuePair = parts;
            break;
        }
    }
    if (!isArray(keyValuePair) || keyValuePair.length < 2) return '';
    {
        let result = keyValuePair[1].replace(/\+/g, ' ');
        try {
            result = decodeURIComponent(result);
        } catch  {
            logger.error('Skipping decoding for malformed query param: ' + result);
        }
        return result;
    }
};
const maskQueryParams = function(url, maskedParams, mask) {
    if (!url || !maskedParams || !maskedParams.length) return url;
    const splitHash = url.split(/#(.*)/);
    const withoutHash = splitHash[0] || '';
    const hash = splitHash[1];
    const splitQuery = withoutHash.split(/\?(.*)/);
    const queryString = splitQuery[1];
    const urlWithoutQueryAndHash = splitQuery[0];
    const queryParts = (queryString || '').split('&');
    const paramStrings = [];
    for(let i = 0; i < queryParts.length; i++){
        const pieces = queryParts[i].split('?');
        let j = 0;
        while(j < pieces.length && !maskedParams.includes(pieces[j].split('=')[0]))j++;
        if (j < pieces.length) paramStrings.push(pieces.slice(0, j).concat(pieces[j].split('=')[0] + '=' + mask).join('?'));
        else paramStrings.push(queryParts[i]);
    }
    let result = urlWithoutQueryAndHash;
    if (null != queryString) result += '?' + paramStrings.join('&');
    if (null != hash) result += '#' + hash;
    return result;
};
const _getHashParam = function(hash, param) {
    const matches = hash.match(new RegExp(param + '=([^&]*)'));
    return matches ? matches[1] : null;
};
const isLocalhost = ()=>localDomains.includes(external_globals_mjs_location.hostname);
export { convertToURL } from "./convert-to-url.mjs";
export { isStatusZeroFailureCircuitBreakerTripped, updateStatusZeroFailureCount } from "./request-reachability.mjs";
export { _getHashParam, formDataToQuery, getQueryParam, isLocalhost, jsonStringify, maskQueryParams };
