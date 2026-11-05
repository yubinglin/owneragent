import { jsx, jsxs } from "preact/jsx-runtime";
import { useContext } from "preact/hooks";
import { SurveyContext } from "../surveys-extension-utils.mjs";
import { PostHogLogo } from "./PostHogLogo.mjs";
function BottomSection({ text, submitDisabled, appearance, onSubmit, link, onPreviewSubmit, skipSubmitButton, canGoBack, onBack }) {
    const { isPreviewMode } = useContext(SurveyContext);
    const showBackButton = !!canGoBack && !!onBack;
    const submitButton = !skipSubmitButton && /*#__PURE__*/ jsx("button", {
        className: "form-submit",
        disabled: submitDisabled,
        "aria-label": "Submit survey",
        type: "button",
        onClick: ()=>{
            if (link) window?.open(link);
            if (isPreviewMode) onPreviewSubmit?.();
            else onSubmit();
        },
        children: text
    });
    return /*#__PURE__*/ jsxs("div", {
        className: "bottom-section",
        children: [
            showBackButton ? /*#__PURE__*/ jsxs("div", {
                className: "form-buttons form-buttons-with-back",
                children: [
                    /*#__PURE__*/ jsx("button", {
                        className: "form-back",
                        type: "button",
                        "aria-label": "Go to previous question",
                        onClick: onBack,
                        children: appearance.backButtonText || 'Back'
                    }),
                    submitButton
                ]
            }) : submitButton,
            !appearance.whiteLabel && /*#__PURE__*/ jsx(PostHogLogo, {
                urlParams: {
                    utm_source: 'survey-footer'
                }
            })
        ]
    });
}
export { BottomSection };
