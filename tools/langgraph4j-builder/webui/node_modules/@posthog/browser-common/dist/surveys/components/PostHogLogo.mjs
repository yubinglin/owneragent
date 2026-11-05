import { jsxs } from "preact/jsx-runtime";
import { IconPosthogLogo } from "../icons.mjs";
function PostHogLogo({ urlParams }) {
    const queryString = urlParams ? Object.entries(urlParams).map(([key, value])=>`${encodeURIComponent(key)}=${encodeURIComponent(value)}`).join('&') : '';
    return /*#__PURE__*/ jsxs("a", {
        href: `https://posthog.com/surveys${queryString ? `?${queryString}` : ''}`,
        target: "_blank",
        rel: "noopener",
        className: "footer-branding",
        children: [
            "Survey by ",
            IconPosthogLogo
        ]
    });
}
export { PostHogLogo };
