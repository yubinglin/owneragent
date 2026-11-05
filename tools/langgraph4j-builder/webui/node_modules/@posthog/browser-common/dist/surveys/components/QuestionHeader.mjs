import { jsx, jsxs } from "preact/jsx-runtime";
import { h } from "preact";
import { useContext } from "preact/hooks";
import { SurveyQuestionType } from "../../survey-constants.mjs";
import { cancelSVG } from "../icons.mjs";
import { SurveyContext, renderChildrenAsTextOrHtml } from "../surveys-extension-utils.mjs";
function QuestionHeader({ question, forceDisableHtml, htmlFor }) {
    const TitleComponent = question.type === SurveyQuestionType.Open ? 'label' : 'h3';
    const isHeaderEmpty = !question.question && !question.description;
    return /*#__PURE__*/ jsxs("div", {
        class: isHeaderEmpty ? 'question-header question-header--empty' : 'question-header',
        children: [
            /*#__PURE__*/ jsx(TitleComponent, {
                className: "survey-question",
                htmlFor: htmlFor,
                children: question.question
            }),
            question.description && renderChildrenAsTextOrHtml({
                component: h('p', {
                    className: "survey-question-description"
                }),
                children: question.description,
                renderAsHtml: !forceDisableHtml && 'text' !== question.descriptionContentType
            })
        ]
    });
}
function Cancel({ onClick }) {
    const { isPreviewMode } = useContext(SurveyContext);
    return /*#__PURE__*/ jsx("button", {
        className: "form-cancel",
        onClick: onClick,
        disabled: isPreviewMode,
        "aria-label": "Close survey",
        type: "button",
        children: cancelSVG({
            fill: 'black'
        })
    });
}
export { Cancel, QuestionHeader };
