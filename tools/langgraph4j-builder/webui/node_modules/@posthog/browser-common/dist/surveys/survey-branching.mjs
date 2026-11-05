import { SurveyQuestionBranchingType, SurveyQuestionType } from "../survey-constants.mjs";
import { createLogger } from "../utils/logger.mjs";
import { isNullish } from "@posthog/core";
const logger = createLogger('[Surveys]');
function getRatingBucketForResponseValue(responseValue, scale) {
    if (2 === scale) {
        if (responseValue < 1 || responseValue > 2) throw new Error('The response must be in range 1-2');
        return 1 === responseValue ? 'positive' : 'negative';
    }
    if (3 === scale) {
        if (responseValue < 1 || responseValue > 3) throw new Error('The response must be in range 1-3');
        return 1 === responseValue ? 'negative' : 2 === responseValue ? 'neutral' : 'positive';
    }
    if (5 === scale) {
        if (responseValue < 1 || responseValue > 5) throw new Error('The response must be in range 1-5');
        return responseValue <= 2 ? 'negative' : 3 === responseValue ? 'neutral' : 'positive';
    }
    if (7 === scale) {
        if (responseValue < 1 || responseValue > 7) throw new Error('The response must be in range 1-7');
        return responseValue <= 3 ? 'negative' : 4 === responseValue ? 'neutral' : 'positive';
    }
    if (10 === scale) {
        if (responseValue < 0 || responseValue > 10) throw new Error('The response must be in range 0-10');
        return responseValue <= 6 ? 'detractors' : responseValue <= 8 ? 'passives' : 'promoters';
    }
    throw new Error('The scale must be one of: 2, 3, 5, 7, 10');
}
function getNextSurveyStep(survey, currentQuestionIndex, response) {
    const question = survey.questions[currentQuestionIndex];
    const nextQuestionIndex = currentQuestionIndex + 1;
    if (!question.branching?.type) {
        if (currentQuestionIndex === survey.questions.length - 1) return SurveyQuestionBranchingType.End;
        return nextQuestionIndex;
    }
    if (question.branching.type === SurveyQuestionBranchingType.End) return SurveyQuestionBranchingType.End;
    if (question.branching.type === SurveyQuestionBranchingType.SpecificQuestion) {
        if (Number.isInteger(question.branching.index)) return question.branching.index;
    } else if (question.branching.type === SurveyQuestionBranchingType.ResponseBased) {
        if (question.type === SurveyQuestionType.SingleChoice) {
            let selectedChoiceIndex = question.choices.indexOf(`${response}`);
            if (-1 === selectedChoiceIndex && question.hasOpenChoice) selectedChoiceIndex = question.choices.length - 1;
            if (question.branching?.responseValues?.hasOwnProperty(selectedChoiceIndex)) {
                const nextStep = question.branching.responseValues[selectedChoiceIndex];
                if (Number.isInteger(nextStep)) return nextStep;
                if (nextStep === SurveyQuestionBranchingType.End) return SurveyQuestionBranchingType.End;
            }
        } else if (question.type === SurveyQuestionType.Rating) {
            if (question.optional && isNullish(response)) return nextQuestionIndex === survey.questions.length ? SurveyQuestionBranchingType.End : nextQuestionIndex;
            if ('number' != typeof response || !Number.isInteger(response)) throw new Error('The response type must be an integer');
            const ratingBucket = getRatingBucketForResponseValue(response, question.scale);
            if (question.branching?.responseValues?.hasOwnProperty(ratingBucket)) {
                const nextStep = question.branching.responseValues[ratingBucket];
                if (Number.isInteger(nextStep)) return nextStep;
                if (nextStep === SurveyQuestionBranchingType.End) return SurveyQuestionBranchingType.End;
            }
        }
        return nextQuestionIndex;
    }
    logger.warn('Falling back to next question index due to unexpected branching type');
    return nextQuestionIndex;
}
export { getNextSurveyStep, getRatingBucketForResponseValue };
