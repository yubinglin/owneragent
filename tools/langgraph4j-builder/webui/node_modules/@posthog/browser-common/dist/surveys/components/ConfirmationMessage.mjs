import { jsx, jsxs } from "preact/jsx-runtime";
import { h } from "preact";
import { SurveyContext, renderChildrenAsTextOrHtml } from "../surveys-extension-utils.mjs";
import { BottomSection } from "./BottomSection.mjs";
import { Cancel } from "./QuestionHeader.mjs";
import { useContext, useEffect } from "preact/hooks";
import { addEventListener } from "../../utils/general-utils.mjs";
function ConfirmationMessage({ header, description, contentType, forceDisableHtml, appearance, onClose }) {
    const { isPopup } = useContext(SurveyContext);
    useEffect(()=>{
        const handleKeyDown = (event)=>{
            if ('Enter' === event.key || 'Escape' === event.key) {
                event.preventDefault();
                onClose();
            }
        };
        addEventListener(window, 'keydown', handleKeyDown);
        return ()=>{
            window.removeEventListener('keydown', handleKeyDown);
        };
    }, [
        onClose
    ]);
    const renderCancelButton = isPopup && true !== appearance.hideCancelButton;
    return /*#__PURE__*/ jsxs("div", {
        className: "thank-you-message",
        role: "status",
        tabIndex: 0,
        "aria-atomic": "true",
        children: [
            renderCancelButton && /*#__PURE__*/ jsx(Cancel, {
                onClick: ()=>onClose()
            }),
            /*#__PURE__*/ jsx("h3", {
                className: "thank-you-message-header",
                children: header
            }),
            description && renderChildrenAsTextOrHtml({
                component: h('p', {
                    className: 'thank-you-message-body'
                }),
                children: description,
                renderAsHtml: !forceDisableHtml && 'text' !== contentType
            }),
            isPopup && /*#__PURE__*/ jsx(BottomSection, {
                text: appearance.thankYouMessageCloseButtonText || 'Close',
                submitDisabled: false,
                appearance: appearance,
                onSubmit: ()=>onClose()
            })
        ]
    });
}
export { ConfirmationMessage };
