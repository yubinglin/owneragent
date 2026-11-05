import { STORED_PERSON_PROPERTIES_KEY } from "../constants.mjs";
import { createLogger } from "../utils/logger.mjs";
import { applySurveyTranslation, detectSurveyLanguage } from "@posthog/core/surveys";
const logger = createLogger('[SurveyTranslations]');
function detectUserLanguage(instance) {
    return detectSurveyLanguage({
        overrideLanguage: instance.config.overrideLanguage,
        storedPersonProperties: instance.client?.kv.get(STORED_PERSON_PROPERTIES_KEY),
        locale: "u" > typeof navigator ? navigator.language || navigator.userLanguage : void 0
    }, logger);
}
function applySurveyTranslationForUser(survey, instance) {
    const userLanguage = detectUserLanguage(instance);
    if (!userLanguage) {
        logger.info('No user language detected');
        return {
            survey,
            language: null
        };
    }
    const result = applySurveyTranslation(survey, userLanguage, logger);
    return {
        survey: result.survey,
        language: result.matchedKey
    };
}
export { applySurveyTranslationForUser, detectUserLanguage };
