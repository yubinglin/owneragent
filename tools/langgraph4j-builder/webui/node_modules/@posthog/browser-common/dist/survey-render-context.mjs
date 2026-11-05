function getSurveyReplayUrl({ client, config }) {
    if (!client || !config.uiHost) return;
    const sessionId = client.session?.sessionId;
    if (!sessionId) return;
    return `${config.uiHost}/project/${client.projectToken}/replay/${sessionId}`;
}
export { getSurveyReplayUrl };
