import { jsx, jsxs } from "preact/jsx-runtime";
import { Fragment } from "preact";
import { useEffect, useMemo, useRef, useState } from "preact/hooks";
import { SurveyQuestionType } from "../../survey-constants.mjs";
import { SurveyValidationType, isArray, isNull, isNumber, isString } from "@posthog/core";
import { getLengthFromRules, getRequirementsHint, getValidationError } from "@posthog/core/surveys";
import { dissatisfiedEmoji, neutralEmoji, satisfiedEmoji, thumbsDownEmoji, thumbsUpEmoji, veryDissatisfiedEmoji, verySatisfiedEmoji } from "../icons.mjs";
import { getDisplayOrderChoices, useSurveyContext } from "../surveys-extension-utils.mjs";
import { BottomSection } from "./BottomSection.mjs";
import { QuestionHeader } from "./QuestionHeader.mjs";
const isValidStringArray = (value)=>isArray(value) && value.every((item)=>isString(item));
const initializeSelectedChoices = (initialValue, questionType)=>{
    if (isString(initialValue)) return initialValue;
    if (isValidStringArray(initialValue)) return initialValue;
    return questionType === SurveyQuestionType.SingleChoice ? null : [];
};
const initializeOpenEndedState = (initialValue, choices)=>{
    if (isString(initialValue) && !choices.includes(initialValue)) return {
        isSelected: true,
        inputValue: initialValue
    };
    if (isValidStringArray(initialValue)) {
        const openEndedValue = initialValue.find((choice)=>!choices.includes(choice));
        if (openEndedValue) return {
            isSelected: true,
            inputValue: openEndedValue
        };
    }
    return {
        isSelected: false,
        inputValue: ''
    };
};
function OpenTextQuestion({ question, forceDisableHtml, appearance, onSubmit, onPreviewSubmit, displayQuestionIndex, initialValue, canGoBack, onBack }) {
    const { isPreviewMode } = useSurveyContext();
    const inputRef = useRef(null);
    const [text, setText] = useState(()=>{
        if (isString(initialValue)) return initialValue;
        return '';
    });
    const disableAutofocus = appearance?.disableAutofocus;
    useEffect(()=>{
        if (disableAutofocus) return;
        setTimeout(()=>{
            if (!isPreviewMode) inputRef.current?.focus();
        }, 100);
    }, [
        isPreviewMode,
        disableAutofocus
    ]);
    const htmlFor = `surveyQuestion${displayQuestionIndex}`;
    const validationError = useMemo(()=>getValidationError(text, question.validation, question.optional), [
        text,
        question.validation,
        question.optional
    ]);
    const minLength = getLengthFromRules(question.validation, SurveyValidationType.MinLength);
    const maxLength = getLengthFromRules(question.validation, SurveyValidationType.MaxLength);
    const requirementsHint = useMemo(()=>getRequirementsHint(minLength, maxLength), [
        minLength,
        maxLength
    ]);
    const handleSubmit = ()=>{
        if (validationError) return;
        onSubmit(text.trim());
    };
    const handlePreviewSubmit = ()=>{
        if (validationError) return;
        onPreviewSubmit(text.trim());
    };
    return /*#__PURE__*/ jsxs(Fragment, {
        children: [
            /*#__PURE__*/ jsxs("div", {
                className: "question-container",
                children: [
                    /*#__PURE__*/ jsx(QuestionHeader, {
                        question: question,
                        forceDisableHtml: forceDisableHtml,
                        htmlFor: htmlFor
                    }),
                    /*#__PURE__*/ jsx("textarea", {
                        ref: inputRef,
                        id: htmlFor,
                        rows: 4,
                        placeholder: appearance?.placeholder,
                        minLength: minLength,
                        maxLength: maxLength,
                        onInput: (e)=>{
                            setText(e.currentTarget.value);
                            e.stopPropagation();
                        },
                        onKeyDown: (e)=>{
                            e.stopPropagation();
                            if ('Enter' === e.key && (e.metaKey || e.ctrlKey) && !validationError) {
                                e.preventDefault();
                                isPreviewMode ? handlePreviewSubmit() : handleSubmit();
                            }
                        },
                        value: text
                    }),
                    requirementsHint && /*#__PURE__*/ jsx("div", {
                        className: "validation-hint",
                        children: requirementsHint
                    })
                ]
            }),
            /*#__PURE__*/ jsx(BottomSection, {
                text: question.buttonText || 'Submit',
                submitDisabled: !!validationError,
                appearance: appearance,
                onSubmit: handleSubmit,
                onPreviewSubmit: handlePreviewSubmit,
                canGoBack: canGoBack,
                onBack: onBack
            })
        ]
    });
}
function LinkQuestion({ question, forceDisableHtml, appearance, onSubmit, onPreviewSubmit, canGoBack, onBack }) {
    return /*#__PURE__*/ jsxs(Fragment, {
        children: [
            /*#__PURE__*/ jsx("div", {
                className: "question-container",
                children: /*#__PURE__*/ jsx(QuestionHeader, {
                    question: question,
                    forceDisableHtml: forceDisableHtml
                })
            }),
            /*#__PURE__*/ jsx(BottomSection, {
                text: question.buttonText || 'Submit',
                submitDisabled: false,
                link: question.link,
                appearance: appearance,
                onSubmit: ()=>onSubmit('link clicked'),
                onPreviewSubmit: ()=>onPreviewSubmit('link clicked'),
                canGoBack: canGoBack,
                onBack: onBack
            })
        ]
    });
}
function RatingQuestion({ question, forceDisableHtml, displayQuestionIndex, appearance, onSubmit, onPreviewSubmit, initialValue, canGoBack, onBack }) {
    const scale = question.scale;
    const starting = 10 === question.scale ? 0 : 1;
    const [rating, setRating] = useState(()=>{
        if (isNumber(initialValue)) return initialValue;
        if (isArray(initialValue) && initialValue.length > 0 && isNumber(parseInt(initialValue[0]))) return parseInt(initialValue[0]);
        if (isString(initialValue) && isNumber(parseInt(initialValue))) return parseInt(initialValue);
        return null;
    });
    const isThumbSurvey = 'emoji' === question.display && 2 === question.scale;
    const { isPreviewMode } = useSurveyContext();
    const handleSubmit = (num)=>{
        if (isPreviewMode) return onPreviewSubmit(num);
        return onSubmit(num);
    };
    return /*#__PURE__*/ jsxs(Fragment, {
        children: [
            /*#__PURE__*/ jsxs("div", {
                className: "question-container",
                children: [
                    /*#__PURE__*/ jsx(QuestionHeader, {
                        question: question,
                        forceDisableHtml: forceDisableHtml
                    }),
                    /*#__PURE__*/ jsxs("div", {
                        className: "rating-section",
                        children: [
                            /*#__PURE__*/ jsxs("div", {
                                className: "rating-options",
                                children: [
                                    'emoji' === question.display && /*#__PURE__*/ jsx("div", {
                                        className: `rating-options-emoji${isThumbSurvey ? ' rating-options-emoji-2' : ''}`,
                                        children: emojiScaleLists[question.scale]?.map((emoji, idx)=>{
                                            const active = idx + 1 === rating;
                                            return /*#__PURE__*/ jsx("button", {
                                                "aria-label": `Rate ${idx + 1}`,
                                                className: `ratings-emoji question-${displayQuestionIndex}-rating-${idx} ${active ? 'rating-active' : ''}`,
                                                value: idx + 1,
                                                type: "button",
                                                onClick: ()=>{
                                                    const response = idx + 1;
                                                    setRating(response);
                                                    if (question.skipSubmitButton) handleSubmit(response);
                                                },
                                                children: emoji
                                            }, idx);
                                        })
                                    }),
                                    'number' === question.display && /*#__PURE__*/ jsx("div", {
                                        className: "rating-options-number",
                                        style: {
                                            gridTemplateColumns: `repeat(${scale - starting + 1}, minmax(0, 1fr))`
                                        },
                                        children: getScaleNumbers(question.scale).map((number, idx)=>{
                                            const active = rating === number;
                                            return /*#__PURE__*/ jsx(RatingButton, {
                                                displayQuestionIndex: displayQuestionIndex,
                                                active: active,
                                                appearance: appearance,
                                                num: number,
                                                setActiveNumber: (response)=>{
                                                    setRating(response);
                                                    if (question.skipSubmitButton) handleSubmit(response);
                                                }
                                            }, idx);
                                        })
                                    })
                                ]
                            }),
                            !isThumbSurvey && /*#__PURE__*/ jsxs("div", {
                                className: "rating-text",
                                children: [
                                    /*#__PURE__*/ jsx("div", {
                                        children: question.lowerBoundLabel
                                    }),
                                    /*#__PURE__*/ jsx("div", {
                                        children: question.upperBoundLabel
                                    })
                                ]
                            })
                        ]
                    })
                ]
            }),
            /*#__PURE__*/ jsx(BottomSection, {
                text: question.buttonText || appearance?.submitButtonText || 'Submit',
                submitDisabled: isNull(rating) && !question.optional,
                appearance: appearance,
                onSubmit: ()=>onSubmit(rating),
                onPreviewSubmit: ()=>onPreviewSubmit(rating),
                skipSubmitButton: question.skipSubmitButton,
                canGoBack: canGoBack,
                onBack: onBack
            })
        ]
    });
}
function RatingButton({ num, active, displayQuestionIndex, setActiveNumber }) {
    return /*#__PURE__*/ jsx("button", {
        "aria-label": `Rate ${num}`,
        className: `ratings-number question-${displayQuestionIndex}-rating-${num} ${active ? 'rating-active' : ''}`,
        type: "button",
        onClick: ()=>{
            setActiveNumber(num);
        },
        children: num
    });
}
function MultipleChoiceQuestion({ question, forceDisableHtml, displayQuestionIndex, appearance, onSubmit, onPreviewSubmit, initialValue, canGoBack, onBack }) {
    const openChoiceInputRef = useRef(null);
    const choices = useMemo(()=>getDisplayOrderChoices(question), [
        question
    ]);
    const [selectedChoices, setSelectedChoices] = useState(()=>initializeSelectedChoices(initialValue, question.type));
    const [openEndedState, setOpenEndedState] = useState(()=>initializeOpenEndedState(initialValue, choices));
    const { isPreviewMode } = useSurveyContext();
    const isSingleChoiceQuestion = question.type === SurveyQuestionType.SingleChoice;
    const isMultipleChoiceQuestion = question.type === SurveyQuestionType.MultipleChoice;
    const shouldSkipSubmit = question.skipSubmitButton && isSingleChoiceQuestion && !question.hasOpenChoice;
    const handleChoiceChange = (val, isOpenChoice)=>{
        if (isOpenChoice) {
            const newOpenSelected = !openEndedState.isSelected;
            setOpenEndedState((prev)=>({
                    ...prev,
                    isSelected: newOpenSelected,
                    inputValue: newOpenSelected ? prev.inputValue : ''
                }));
            if (isSingleChoiceQuestion) setSelectedChoices('');
            if (newOpenSelected) setTimeout(()=>openChoiceInputRef.current?.focus(), 75);
            return;
        }
        if (isSingleChoiceQuestion) {
            setSelectedChoices(val);
            setOpenEndedState((prev)=>({
                    ...prev,
                    isSelected: false,
                    inputValue: ''
                }));
            if (shouldSkipSubmit) {
                onSubmit(val);
                if (isPreviewMode) onPreviewSubmit(val);
            }
            return;
        }
        if (isMultipleChoiceQuestion && isArray(selectedChoices)) selectedChoices.includes(val) ? setSelectedChoices(selectedChoices.filter((choice)=>choice !== val)) : setSelectedChoices([
            ...selectedChoices,
            val
        ]);
    };
    const handleOpenEndedInputChange = (e)=>{
        e.stopPropagation();
        const newValue = e.currentTarget.value;
        setOpenEndedState((prev)=>({
                ...prev,
                inputValue: newValue
            }));
        if (isSingleChoiceQuestion) setSelectedChoices(newValue);
    };
    const handleOpenEndedKeyDown = (e)=>{
        e.stopPropagation();
        if ('Enter' === e.key && !isSubmitDisabled()) {
            e.preventDefault();
            handleSubmit();
        }
        if ('Escape' === e.key) {
            e.preventDefault();
            setOpenEndedState((prev)=>({
                    ...prev,
                    isSelected: false,
                    inputValue: ''
                }));
            if (isSingleChoiceQuestion) setSelectedChoices(null);
        }
    };
    const isSubmitDisabled = ()=>{
        if (question.optional) return false;
        if (isNull(selectedChoices)) return true;
        if (isArray(selectedChoices)) {
            if (!openEndedState.isSelected && 0 === selectedChoices.length) return true;
        }
        if (openEndedState.isSelected && '' === openEndedState.inputValue.trim()) return true;
        return false;
    };
    const handleSubmit = ()=>{
        if (openEndedState.isSelected && isMultipleChoiceQuestion) {
            if (isArray(selectedChoices)) isPreviewMode ? onPreviewSubmit([
                ...selectedChoices,
                openEndedState.inputValue
            ]) : onSubmit([
                ...selectedChoices,
                openEndedState.inputValue
            ]);
        } else isPreviewMode ? onPreviewSubmit(selectedChoices) : onSubmit(selectedChoices);
    };
    return /*#__PURE__*/ jsxs(Fragment, {
        children: [
            /*#__PURE__*/ jsxs("div", {
                className: "question-container",
                children: [
                    /*#__PURE__*/ jsx(QuestionHeader, {
                        question: question,
                        forceDisableHtml: forceDisableHtml
                    }),
                    /*#__PURE__*/ jsxs("fieldset", {
                        className: "multiple-choice-options limit-height",
                        children: [
                            /*#__PURE__*/ jsx("legend", {
                                className: "sr-only",
                                children: isMultipleChoiceQuestion ? ' Select all that apply' : ' Select one'
                            }),
                            choices.map((choice, idx)=>{
                                const isOpenChoice = !!question.hasOpenChoice && idx === question.choices.length - 1;
                                const inputId = `surveyQuestion${displayQuestionIndex}Choice${idx}`;
                                const openInputId = `${inputId}Open`;
                                const isChecked = isOpenChoice ? openEndedState.isSelected : isSingleChoiceQuestion ? selectedChoices === choice : isArray(selectedChoices) && selectedChoices.includes(choice);
                                return /*#__PURE__*/ jsxs("label", {
                                    className: isOpenChoice ? 'choice-option-open' : '',
                                    children: [
                                        /*#__PURE__*/ jsxs("div", {
                                            className: "response-choice",
                                            children: [
                                                /*#__PURE__*/ jsx("input", {
                                                    type: isSingleChoiceQuestion ? 'radio' : 'checkbox',
                                                    name: inputId,
                                                    checked: isChecked,
                                                    onChange: ()=>handleChoiceChange(choice, isOpenChoice),
                                                    id: inputId,
                                                    "aria-controls": openInputId
                                                }),
                                                /*#__PURE__*/ jsx("span", {
                                                    children: isOpenChoice ? `${choice}:` : choice
                                                })
                                            ]
                                        }),
                                        isOpenChoice && /*#__PURE__*/ jsx("input", {
                                            type: "text",
                                            ref: openChoiceInputRef,
                                            id: openInputId,
                                            name: `question${displayQuestionIndex}Open`,
                                            value: openEndedState.inputValue,
                                            onKeyDown: handleOpenEndedKeyDown,
                                            onInput: handleOpenEndedInputChange,
                                            onClick: (e)=>{
                                                if (!openEndedState.isSelected) handleChoiceChange(choice, true);
                                                e.stopPropagation();
                                            },
                                            "aria-label": `${choice} - please specify`
                                        })
                                    ]
                                }, idx);
                            })
                        ]
                    })
                ]
            }),
            /*#__PURE__*/ jsx(BottomSection, {
                text: question.buttonText || 'Submit',
                submitDisabled: isSubmitDisabled(),
                appearance: appearance,
                onSubmit: handleSubmit,
                onPreviewSubmit: handleSubmit,
                skipSubmitButton: shouldSkipSubmit,
                canGoBack: canGoBack,
                onBack: onBack
            })
        ]
    });
}
const fiveScaleNumbers = [
    1,
    2,
    3,
    4,
    5
];
const sevenScaleNumbers = [
    1,
    2,
    3,
    4,
    5,
    6,
    7
];
const tenScaleNumbers = [
    0,
    1,
    2,
    3,
    4,
    5,
    6,
    7,
    8,
    9,
    10
];
const emojiScaleLists = {
    2: [
        thumbsUpEmoji,
        thumbsDownEmoji
    ],
    3: [
        dissatisfiedEmoji,
        neutralEmoji,
        satisfiedEmoji
    ],
    5: [
        veryDissatisfiedEmoji,
        dissatisfiedEmoji,
        neutralEmoji,
        satisfiedEmoji,
        verySatisfiedEmoji
    ]
};
function getScaleNumbers(scale) {
    switch(scale){
        case 5:
            return fiveScaleNumbers;
        case 7:
            return sevenScaleNumbers;
        case 10:
            return tenScaleNumbers;
        default:
            return fiveScaleNumbers;
    }
}
export { LinkQuestion, MultipleChoiceQuestion, OpenTextQuestion, RatingButton, RatingQuestion };
