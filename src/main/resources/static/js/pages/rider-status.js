'use strict';

if (window.history?.replaceState) {
    const url = new URL(window.location.href);

    if (url.searchParams.has('ride')) {
        url.searchParams.delete('ride');
        window.history.replaceState(
            {},
            document.title,
            url.pathname + url.search + url.hash
        );
    }
}
