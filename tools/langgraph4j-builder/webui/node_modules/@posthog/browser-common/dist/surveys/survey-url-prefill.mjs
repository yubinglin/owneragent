import { SurveyQuestionBranchingType, SurveyQuestionType } from "../survey-constants.mjs";
import { getSurveyResponseKey } from "./surveys-extension-utils.mjs";
import { logger } from "../utils/logger.mjs";
import { isUndefined } from "@posthog/core";
import { getNextSurveyStep } from "./survey-branching.mjs";
function extractPrefillParamsFromUrl(searchString) {
    const params = {};
    let autoSubmit = false;
    const cleanSearch = searchString.replace(/^\?/, '');
    if (!cleanSearch) return {
        params,
        autoSubmit
    };
    const pairs = cleanSearch.split('&');
    for (const pair of pairs){
        const separator = pair.indexOf('=');
        if (separator <= 0) continue;
        let decodedKey;
        let decodedValue;
        try {
            decodedKey = decodeURIComponent(pair.slice(0, separator));
            decodedValue = decodeURIComponent(pair.slice(separator + 1));
        } catch  {
            continue;
        }
        if ('auto_submit' === decodedKey && 'true' === decodedValue) {
            autoSubmit = true;
            continue;
        }
        const match = decodedKey.match(/^q(\d+)$/);
        if (match) {
            const questionIndex = parseInt(match[1], 10);
            if (!params[questionIndex]) params[questionIndex] = [];
            params[questionIndex].push(decodedValue);
        }
    }
    return {
        params,
        autoSubmit
    };
}
function convertPrefillToResponses(survey, prefillParams) {
    const responses = {};
    survey.questions.forEach((question, index)=>{
        if (!prefillParams[index] || !question.id) return;
        const values = prefillParams[index];
        const responseKey = getSurveyResponseKey(question.id);
        try {
            switch(question.type){
                case SurveyQuestionType.SingleChoice:
                    {
                        if (!question.choices || 0 === question.choices.length) return void logger.warn(`[Survey Prefill] Question ${index} has no choices`);
                        const choiceIndex = parseInt(values[0], 10);
                        if (isNaN(choiceIndex) || choiceIndex < 0 || choiceIndex >= question.choices.length) return void logger.warn(`[Survey Prefill] Invalid choice index for q${index}: ${values[0]}`);
                        responses[responseKey] = question.choices[choiceIndex];
                        break;
                    }
                case SurveyQuestionType.MultipleChoice:
                    {
                        if (!question.choices || 0 === question.choices.length) return void logger.warn(`[Survey Prefill] Question ${index} has no choices`);
                        const choiceIndices = values.map((v)=>parseInt(v, 10)).filter((i)=>!isNaN(i) && i >= 0 && i < question.choices.length);
                        if (0 === choiceIndices.length) return void logger.warn(`[Survey Prefill] No valid choices for q${index}`);
                        const uniqueChoices = [
                            ...new Set(choiceIndices.map((i)=>question.choices[i]))
                        ];
                        if (uniqueChoices.length < choiceIndices.length) logger.warn(`[Survey Prefill] Removed duplicate choices for q${index}`);
                        responses[responseKey] = uniqueChoices;
                        break;
                    }
                case SurveyQuestionType.Rating:
                    {
                        const rating = parseInt(values[0], 10);
                        const scale = question.scale || 10;
                        const min = 10 === scale ? 0 : 1;
                        if (isNaN(rating) || rating < min || rating > scale) return void logger.warn(`[Survey Prefill] Invalid rating for q${index}: ${values[0]} (scale: ${min}-${scale})`);
                        responses[responseKey] = rating;
                        break;
                    }
                default:
                    logger.info(`[Survey Prefill] Question type ${question.type} does not support prefill`);
            }
        } catch (error) {
            logger.error(`[Survey Prefill] Error converting q${index}:`, error);
        }
    });
    return responses;
}
function calculatePrefillStartIndex(survey, prefilledIndices, responses) {
    let currentIndex = 0;
    const skippedResponses = {};
    const skippedIndices = [];
    const MAX_ITERATIONS = survey.questions.length + 1;
    let iterations = 0;
    while(currentIndex < survey.questions.length && iterations < MAX_ITERATIONS){
        iterations++;
        if (!prefilledIndices.includes(currentIndex)) break;
        const question = survey.questions[currentIndex];
        if (!question || !('skipSubmitButton' in question) || !question.skipSubmitButton) break;
        if (question.id) {
            const responseKey = getSurveyResponseKey(question.id);
            if (!isUndefined(responses[responseKey])) skippedResponses[responseKey] = responses[responseKey];
        }
        skippedIndices.push(currentIndex);
        const response = question.id ? responses[getSurveyResponseKey(question.id)] : null;
        const nextStep = getNextSurveyStep(survey, currentIndex, response);
        if (nextStep === SurveyQuestionBranchingType.End) return {
            startQuestionIndex: survey.questions.length,
            skippedResponses,
            skippedIndices
        };
        currentIndex = nextStep;
    }
    return {
        startQuestionIndex: currentIndex,
        skippedResponses,
        skippedIndices
    };
}
export { calculatePrefillStartIndex, convertPrefillToResponses, extractPrefillParamsFromUrl };
