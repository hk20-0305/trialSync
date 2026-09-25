import { useCallback, useRef } from 'react';
import { useBeforeUnload, useBlocker } from 'react-router-dom';
const MESSAGE = 'You have unsaved patient changes.';
export function useUnsavedChanges(when) {
    const bypassNextNavigation = useRef(false);
    const blocker = useBlocker(useCallback(({ currentLocation, nextLocation }) => when &&
        !bypassNextNavigation.current &&
        (currentLocation.pathname !== nextLocation.pathname ||
            currentLocation.search !== nextLocation.search), [when]));
    useBeforeUnload(useCallback((event) => {
        if (!when || bypassNextNavigation.current)
            return;
        event.preventDefault();
        event.returnValue = MESSAGE;
    }, [when]), { capture: true });
    return {
        isBlocked: blocker.state === 'blocked',
        stay: () => {
            if (blocker.state === 'blocked')
                blocker.reset();
        },
        discardAndContinue: () => {
            if (blocker.state === 'blocked')
                blocker.proceed();
        },
        allowNextNavigation: () => {
            bypassNextNavigation.current = true;
        },
    };
}
