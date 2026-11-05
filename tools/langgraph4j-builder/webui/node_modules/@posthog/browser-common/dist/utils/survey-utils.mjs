import { doesSurveyActivateByEvent, getSurveyInteractionProperty, getSurveyIterationKey, isSurveyIterationBased } from "@posthog/core/surveys";
import { DisplaySurveyType, SurveyType } from "../survey-constants.mjs";
import { createLogger } from "./logger.mjs";
const SURVEY_LOGGER = createLogger('[Surveys]');
const SURVEY_CAPTURING_DISABLED = 'PostHog is not capturing, so a survey response cannot be recorded';
function isSurveyRunning(survey) {
    return !!(survey.start_date && !survey.end_date);
}
function doesSurveyActivateByAction(survey) {
    return !!survey.conditions?.actions?.values?.length;
}
const SURVEY_SEEN_PREFIX = 'seenSurvey_';
const SURVEY_IN_PROGRESS_PREFIX = 'inProgressSurvey_';
const SURVEY_ABANDONED_PREFIX = 'abandonedSurvey_';
const getSurveyStorageKey = (prefix, survey)=>`${prefix}${getSurveyIterationKey(survey)}`;
const getSurveySeenKey = (survey)=>getSurveyStorageKey(SURVEY_SEEN_PREFIX, survey);
const getSurveyAbandonedKey = (survey)=>getSurveyStorageKey(SURVEY_ABANDONED_PREFIX, survey);
const setSurveySeenOnLocalStorage = (survey)=>{
    try {
        const surveySeenKey = getSurveySeenKey(survey);
        const isSurveySeen = localStorage.getItem(surveySeenKey);
        if (isSurveySeen) return;
        localStorage.setItem(surveySeenKey, 'true');
    } catch (error) {
        SURVEY_LOGGER.error('Failed to persist survey seen state', error);
    }
};
const IN_APP_SURVEY_TYPES = [
    SurveyType.Popover,
    SurveyType.Widget,
    SurveyType.API
];
const DEFAULT_DISPLAY_SURVEY_OPTIONS = {
    ignoreConditions: false,
    ignoreDelay: false,
    displayType: DisplaySurveyType.Popover
};
export { DEFAULT_DISPLAY_SURVEY_OPTIONS, IN_APP_SURVEY_TYPES, SURVEY_ABANDONED_PREFIX, SURVEY_CAPTURING_DISABLED, SURVEY_IN_PROGRESS_PREFIX, SURVEY_LOGGER, SURVEY_SEEN_PREFIX, doesSurveyActivateByAction, doesSurveyActivateByEvent, getSurveyAbandonedKey, getSurveyInteractionProperty, getSurveySeenKey, getSurveyStorageKey, isSurveyIterationBased, isSurveyRunning, setSurveySeenOnLocalStorage };
