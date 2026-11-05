import { jsx, jsxs } from "preact/jsx-runtime";
import { h } from "preact";
import { SurveyContext, renderChildrenAsTextOrHtml } from "../surveys-extension-utils.mjs";
import { BottomSection } from "./BottomSection.mjs";
import { Cancel } from "./QuestionHeader.mjs";
import { useContext } from "preact/hooks";
function IntroScreen({ header, description, contentType, forceDisableHtml, appearance, onStart }) {
    const { isPopup, onPreviewSubmit, onPopupSurveyDismissed } = useContext(SurveyContext);
    const renderCancelButton = isPopup && true !== appearance.hideCancelButton;
    return /*#__PURE__*/ jsxs("div", {
        className: "intro-screen",
        role: "status",
        tabIndex: 0,
        "aria-atomic": "true",
        children: [
            renderCancelButton && /*#__PURE__*/ jsx(Cancel, {
                onClick: ()=>onPopupSurveyDismissed()
            }),
            header && /*#__PURE__*/ jsx("h3", {
                className: "intro-screen-header",
                children: header
            }),
            description && renderChildrenAsTextOrHtml({
                component: h('p', {
                    className: 'intro-screen-body'
                }),
                children: description,
                renderAsHtml: !forceDisableHtml && 'text' !== contentType
            }),
            /*#__PURE__*/ jsx(BottomSection, {
                text: appearance.introScreenButtonText || 'Get started',
                submitDisabled: false,
                appearance: appearance,
                onSubmit: onStart,
                onPreviewSubmit: ()=>onPreviewSubmit(null)
            })
        ]
    });
}
export { IntroScreen };
