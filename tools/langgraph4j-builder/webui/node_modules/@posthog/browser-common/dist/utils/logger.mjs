import config from "../config.mjs";
import { isUndefined } from "@posthog/core";
const _createLogger = (prefix, { debugEnabled } = {})=>{
    const logger = {
        _log: (level, ...args)=>{
            let browserWindow;
            try {
                browserWindow = "u" < typeof window ? void 0 : window;
            } catch  {
                return;
            }
            if (browserWindow && (config.DEBUG || browserWindow.POSTHOG_DEBUG || debugEnabled) && !isUndefined(browserWindow.console) && browserWindow.console) {
                const consoleLog = '__rrweb_original__' in browserWindow.console[level] ? browserWindow.console[level]['__rrweb_original__'] : browserWindow.console[level];
                consoleLog(prefix, ...args);
            }
        },
        debug: (...args)=>{
            logger._log('debug', ...args);
        },
        info: (...args)=>{
            logger._log('log', ...args);
        },
        warn: (...args)=>{
            logger._log('warn', ...args);
        },
        error: (...args)=>{
            logger._log('error', ...args);
        },
        critical: (...args)=>{
            console.error(prefix, ...args);
        },
        uninitializedWarning: (methodName)=>{
            logger.error(`You must initialize PostHog before calling ${methodName}`);
        },
        createLogger: (additionalPrefix, options)=>_createLogger(`${prefix} ${additionalPrefix}`, options)
    };
    return logger;
};
const logger_logger = _createLogger('[PostHog.js]');
const createLogger = logger_logger.createLogger;
export { createLogger, logger_logger as logger };
