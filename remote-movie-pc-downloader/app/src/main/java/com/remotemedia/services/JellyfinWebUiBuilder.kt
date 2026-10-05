package com.remotemedia.services

/**
 * Modern Editorial UI Generator for PN-NODE × JELLYFIN
 * Pixel-perfect implementation of the Stitch design system for TV, Desktop, and Mobile.
 */
object JellyfinWebUiBuilder {

    fun build(
        allVideoCount: Int,
        firstStreamUrl: String,
        firstTitle: String,
        spotlightTitle: String,
        spotlightSub: String,
        spotlightDesc: String,
        spotlightThumb: String,
        spotlightBadge: String,
        spotlightResumeSeconds: Long = 0L,
        continueWatchingCardsHtml: String = "",
        recentlyAddedCards: String,
        sandboxCardsHtml: String,
        telegramCardsHtml: String,
        trendingCardsHtml: String,
        queueItemsHtml: String,
        queueCount: Int,
        storageUsedStr: String,
        storageTotalStr: String,
        storagePercent: Int,
        offlineFilesHtml: String
    ): String {
        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
            <title>PN-NODE × JELLYFIN — Self-Hosted Media Server &amp; Client</title>
            <link rel="preconnect" href="https://fonts.googleapis.com">
            <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
            <link href="https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;600;700&family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap" rel="stylesheet">
            <style>
                :root {
                    --pn-red: #E11D48;
                    --pn-red-hover: #BE123C;
                    --pn-red-dark: #9F1239;
                    --pn-red-soft: #FFE4E6;
                    --pn-red-border: rgba(225, 29, 72, 0.2);
                    --bg-canvas: #F8FAFC;
                    --bg-surface: #FFFFFF;
                    --bg-subtle: #F1F5F9;
                    --text-heading: #0F172A;
                    --text-body: #334155;
                    --text-muted: #64748B;
                    --text-light: #94A3B8;
                    --border-light: #E2E8F0;
                    --border-subtle: #F1F5F9;
                    --pill-navy: #1E293B;
                    --teal-accent: #0F766E;
                    --teal-soft: #CCFBF1;
                    --amber-soft: #FEF3C7;
                    --amber-accent: #B45309;
                    --shadow-sm: 0 1px 3px rgba(15, 23, 42, 0.04), 0 1px 2px rgba(15, 23, 42, 0.02);
                    --shadow-md: 0 4px 14px rgba(15, 23, 42, 0.06);
                    --shadow-lg: 0 12px 28px rgba(15, 23, 42, 0.08);
                    --font-sans: 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                    --font-mono: 'JetBrains Mono', monospace;
                }

                * { box-sizing: border-box; margin: 0; padding: 0; -webkit-tap-highlight-color: transparent; }
                html, body {
                    background-color: var(--bg-canvas);
                    color: var(--text-heading);
                    font-family: var(--font-sans);
                    width: 100%;
                    min-height: 100%;
                    overflow-x: hidden;
                    -webkit-font-smoothing: antialiased;
                }

                /* Top Header & Navbar (TV & Desktop) */
                header.site-header {
                    position: sticky;
                    top: 0;
                    z-index: 50;
                    background: rgba(255, 255, 255, 0.94);
                    backdrop-filter: blur(16px);
                    -webkit-backdrop-filter: blur(16px);
                    border-bottom: 1px solid var(--border-light);
                    padding: 10px clamp(16px, 3vw, 44px);
                }
                .nav-container {
                    max-width: 1720px;
                    margin: 0 auto;
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    gap: 16px;
                }
                .brand-wrap {
                    display: flex;
                    align-items: center;
                    gap: 14px;
                    text-decoration: none;
                    cursor: pointer;
                }
                .logo-squircle {
                    width: 44px;
                    height: 44px;
                    border-radius: 12px;
                    background: var(--pn-red);
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    box-shadow: 0 4px 12px rgba(225, 29, 72, 0.28);
                    flex-shrink: 0;
                }
                .brand-meta {
                    display: flex;
                    flex-direction: column;
                    gap: 2px;
                }
                .brand-title-row {
                    display: flex;
                    align-items: center;
                    gap: 6px;
                    font-weight: 800;
                    font-size: 17px;
                    letter-spacing: -0.5px;
                }
                .pn-part { color: var(--pn-red); }
                .pn-cross {
                    background: #E2E8F0;
                    color: #475569;
                    font-size: 11px;
                    font-weight: 800;
                    padding: 1px 5px;
                    border-radius: 4px;
                }
                .jf-part { color: var(--text-heading); }
                .brand-sub {
                    font-family: var(--font-mono);
                    font-size: 9.5px;
                    font-weight: 700;
                    letter-spacing: 0.8px;
                    color: var(--text-muted);
                }
                .brand-pills-row {
                    display: flex;
                    gap: 5px;
                    margin-top: 1px;
                }
                .pill-tag {
                    font-size: 9px;
                    font-weight: 800;
                    padding: 1px 6px;
                    border-radius: 4px;
                    letter-spacing: 0.4px;
                    text-transform: uppercase;
                }
                .pill-hl { background: var(--pn-red-soft); color: var(--pn-red-hover); }
                .pill-tc { background: #E2E8F0; color: #334155; }
                .pill-dp { background: var(--teal-soft); color: var(--teal-accent); }

                /* Navigation Tabs */
                .nav-tabs {
                    display: flex;
                    align-items: center;
                    gap: 6px;
                }
                .nav-tab-btn {
                    padding: 7px 18px;
                    border-radius: 20px;
                    font-size: 13.5px;
                    font-weight: 600;
                    color: var(--text-muted);
                    background: transparent;
                    border: none;
                    cursor: pointer;
                    transition: all 0.15s ease;
                    font-family: inherit;
                }
                .nav-tab-btn:hover {
                    color: var(--text-heading);
                    background: var(--bg-subtle);
                }
                .nav-tab-btn.active {
                    background: var(--pn-red);
                    color: #FFFFFF;
                    font-weight: 700;
                    box-shadow: 0 2px 8px rgba(225, 29, 72, 0.35);
                }

                /* Search & Actions */
                .nav-right-actions {
                    display: flex;
                    align-items: center;
                    gap: 12px;
                }
                .search-box-pill {
                    display: flex;
                    align-items: center;
                    background: var(--bg-surface);
                    border: 1px solid var(--border-light);
                    border-radius: 24px;
                    padding: 0 14px;
                    height: 38px;
                    gap: 9px;
                    width: clamp(220px, 22vw, 340px);
                    box-shadow: var(--shadow-sm);
                    transition: border-color 0.15s, box-shadow 0.15s;
                }
                .search-box-pill:focus-within {
                    border-color: var(--pn-red);
                    box-shadow: 0 0 0 3px var(--pn-red-soft);
                }
                .search-input-field {
                    border: none;
                    background: none;
                    outline: none;
                    font-family: inherit;
                    font-size: 13px;
                    color: var(--text-heading);
                    width: 100%;
                }
                .search-input-field::placeholder { color: var(--text-light); }
                .kbd-badge {
                    font-family: var(--font-mono);
                    font-size: 10px;
                    font-weight: 700;
                    background: var(--bg-subtle);
                    color: var(--text-muted);
                    padding: 2px 6px;
                    border-radius: 6px;
                    border: 1px solid var(--border-light);
                    white-space: nowrap;
                }
                .icon-action-btn {
                    width: 38px;
                    height: 38px;
                    border-radius: 50%;
                    border: 1px solid var(--border-light);
                    background: var(--bg-surface);
                    color: var(--text-body);
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    cursor: pointer;
                    position: relative;
                    transition: all 0.15s ease;
                }
                .icon-action-btn:hover {
                    border-color: var(--pn-red);
                    color: var(--pn-red);
                    background: var(--pn-red-soft);
                }
                .btn-dot {
                    width: 6px;
                    height: 6px;
                    border-radius: 50%;
                    background: var(--pn-red);
                    position: absolute;
                    top: 8px;
                    right: 8px;
                }
                .user-avatar-pill {
                    display: flex;
                    align-items: center;
                    gap: 8px;
                    padding: 4px 10px 4px 4px;
                    background: var(--bg-surface);
                    border: 1px solid var(--border-light);
                    border-radius: 20px;
                    cursor: pointer;
                    font-size: 12.5px;
                    font-weight: 600;
                }
                .avatar-circle {
                    width: 28px;
                    height: 28px;
                    border-radius: 50%;
                    background: var(--pill-navy);
                    color: #FFFFFF;
                    font-weight: 800;
                    font-size: 12px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    position: relative;
                }
                .avatar-online-dot {
                    position: absolute;
                    bottom: 0;
                    right: 0;
                    width: 8px;
                    height: 8px;
                    border-radius: 50%;
                    background: #22C55E;
                    border: 1.5px solid #FFFFFF;
                }

                /* Mobile Top Header (Hidden on TV/Desktop) */
                .mobile-header-bar {
                    display: none;
                    align-items: center;
                    justify-content: space-between;
                    padding: 12px 18px;
                    background: #FFFFFF;
                    border-bottom: 1px solid var(--border-light);
                    position: sticky;
                    top: 0;
                    z-index: 50;
                }
                .mobile-logo-title-col {
                    display: flex;
                    align-items: center;
                    gap: 10px;
                }
                .mobile-title-text {
                    font-size: 18px;
                    font-weight: 800;
                    letter-spacing: -0.4px;
                    color: var(--text-heading);
                }
                .mobile-sub-red {
                    font-size: 10.5px;
                    font-weight: 800;
                    color: var(--pn-red);
                    letter-spacing: 0.5px;
                }

                /* Main Body Layout */
                main.page-canvas {
                    max-width: 1720px;
                    margin: 0 auto;
                    padding: clamp(16px, 2.5vw, 36px) clamp(16px, 3vw, 44px) 80px;
                    display: flex;
                    flex-direction: column;
                    gap: clamp(24px, 3vw, 44px);
                }

                /* 1. Curated Hub Header Banner */
                .curated-hub-header {
                    display: flex;
                    flex-direction: column;
                    gap: 12px;
                }
                .curated-meta-row {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    flex-wrap: wrap;
                    gap: 10px;
                }
                .curated-badges-left {
                    display: flex;
                    align-items: center;
                    gap: 8px;
                }
                .curated-tag-red {
                    background: var(--pn-red);
                    color: #FFFFFF;
                    font-size: 10.5px;
                    font-weight: 800;
                    padding: 3px 8px;
                    border-radius: 4px;
                    letter-spacing: 0.4px;
                    text-transform: uppercase;
                }
                .curated-tag-outline {
                    border: 1px solid var(--border-light);
                    background: #FFFFFF;
                    color: var(--text-muted);
                    font-size: 11px;
                    font-weight: 700;
                    padding: 2px 8px;
                    border-radius: 4px;
                }
                .curated-tech-right {
                    font-family: var(--font-mono);
                    font-size: 11px;
                    color: var(--text-muted);
                    font-weight: 600;
                    display: flex;
                    gap: 14px;
                }
                .curated-tech-item {
                    display: flex;
                    align-items: center;
                    gap: 4px;
                }
                .hub-title-actions-row {
                    display: flex;
                    align-items: flex-end;
                    justify-content: space-between;
                    gap: 20px;
                    flex-wrap: wrap;
                }
                .hub-title-col {
                    max-width: 820px;
                }
                .hub-main-h1 {
                    font-size: clamp(26px, 3vw, 42px);
                    font-weight: 800;
                    letter-spacing: -0.8px;
                    color: var(--text-heading);
                    line-height: 1.15;
                    margin-bottom: 6px;
                }
                .hub-sub-text {
                    font-size: clamp(13px, 1.1vw, 15px);
                    color: var(--text-muted);
                    line-height: 1.5;
                }
                .hub-action-btns {
                    display: flex;
                    align-items: center;
                    gap: 10px;
                }
                .play-all-pill-btn {
                    background: var(--pn-red);
                    color: #FFFFFF;
                    border: none;
                    border-radius: 24px;
                    padding: 10px 24px;
                    font-weight: 700;
                    font-size: 14px;
                    display: flex;
                    align-items: center;
                    gap: 8px;
                    cursor: pointer;
                    box-shadow: 0 4px 14px rgba(225, 29, 72, 0.35);
                    transition: background 0.15s ease, transform 0.15s ease;
                }
                .play-all-pill-btn:hover {
                    background: var(--pn-red-hover);
                    transform: translateY(-1px);
                }
                .shuffle-pill-btn {
                    background: #FFFFFF;
                    color: var(--text-heading);
                    border: 1px solid var(--border-light);
                    border-radius: 24px;
                    padding: 10px 20px;
                    font-weight: 700;
                    font-size: 14px;
                    display: flex;
                    align-items: center;
                    gap: 8px;
                    cursor: pointer;
                    box-shadow: var(--shadow-sm);
                    transition: border-color 0.15s ease;
                }
                .shuffle-pill-btn:hover {
                    border-color: var(--text-heading);
                }

                /* 2. Split Row: Spotlight & Queue */
                .spotlight-queue-grid {
                    display: grid;
                    grid-template-columns: 1.65fr 1fr;
                    gap: 24px;
                    align-items: stretch;
                }

                /* Left: Spotlight Card */
                .spotlight-card {
                    background: #FFFFFF;
                    border: 1px solid var(--border-light);
                    border-radius: 18px;
                    overflow: hidden;
                    box-shadow: var(--shadow-sm);
                    display: flex;
                    flex-direction: column;
                }
                .spotlight-backdrop {
                    position: relative;
                    height: 250px;
                    background: #0F172A;
                    overflow: hidden;
                }
                .spotlight-backdrop-img {
                    width: 100%;
                    height: 100%;
                    object-fit: cover;
                    opacity: 0.82;
                    filter: saturate(1.1);
                    transition: transform 0.4s ease;
                }
                .spotlight-card:hover .spotlight-backdrop-img {
                    transform: scale(1.02);
                }
                .spotlight-top-tags {
                    position: absolute;
                    top: 14px;
                    left: 16px;
                    right: 16px;
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    z-index: 2;
                }
                .spotlight-tag-left {
                    display: flex;
                    gap: 8px;
                }
                .tag-chip {
                    font-size: 10.5px;
                    font-weight: 800;
                    padding: 3px 8px;
                    border-radius: 4px;
                    background: rgba(15, 23, 42, 0.75);
                    backdrop-filter: blur(8px);
                    color: #FFFFFF;
                    letter-spacing: 0.5px;
                }
                .tag-chip-red {
                    background: var(--pn-red);
                    color: #FFFFFF;
                }
                .spotlight-backdrop-gradient {
                    position: absolute;
                    inset: 0;
                    background: linear-gradient(180deg, rgba(15, 23, 42, 0.2) 0%, rgba(15, 23, 42, 0.85) 100%);
                }
                .spotlight-body {
                    padding: 22px 24px;
                    display: flex;
                    flex-direction: column;
                    gap: 14px;
                    flex: 1;
                }
                .spotlight-hero-meta {
                    font-family: var(--font-mono);
                    font-size: 11px;
                    font-weight: 700;
                    color: var(--pn-red);
                    letter-spacing: 0.6px;
                    text-transform: uppercase;
                }
                .spotlight-title-row {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    gap: 12px;
                }
                .spotlight-title {
                    font-size: 26px;
                    font-weight: 800;
                    letter-spacing: -0.5px;
                    color: var(--text-heading);
                }
                .connected-tv-badge {
                    display: flex;
                    align-items: center;
                    gap: 6px;
                    background: var(--pn-red-soft);
                    color: var(--pn-red-hover);
                    border: 1px solid var(--pn-red-border);
                    padding: 4px 10px;
                    border-radius: 6px;
                    font-size: 11px;
                    font-weight: 800;
                    letter-spacing: 0.3px;
                }
                .spotlight-synopsis {
                    font-size: 13.5px;
                    line-height: 1.55;
                    color: var(--text-muted);
                }
                .spotlight-spec-chips {
                    display: flex;
                    align-items: center;
                    gap: 8px;
                    flex-wrap: wrap;
                }
                .spec-pill {
                    font-family: var(--font-mono);
                    font-size: 10.5px;
                    font-weight: 700;
                    padding: 3px 8px;
                    background: var(--bg-subtle);
                    border: 1px solid var(--border-light);
                    border-radius: 4px;
                    color: var(--text-body);
                }
                .spec-pill-rating {
                    background: #FEF2F2;
                    border-color: #FECACA;
                    color: var(--pn-red);
                }
                .spotlight-cast {
                    font-size: 12px;
                    color: var(--text-light);
                }
                .spotlight-progress-wrap {
                    display: flex;
                    flex-direction: column;
                    gap: 6px;
                    margin-top: 4px;
                }
                .progress-info-row {
                    display: flex;
                    justify-content: space-between;
                    font-family: var(--font-mono);
                    font-size: 11.5px;
                    color: var(--text-muted);
                }
                .progress-track {
                    height: 5px;
                    background: var(--border-light);
                    border-radius: 3px;
                    overflow: hidden;
                }
                .progress-fill {
                    height: 100%;
                    background: var(--pn-red);
                    border-radius: 3px;
                }
                .spotlight-action-row {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    gap: 12px;
                    margin-top: 6px;
                    padding-top: 14px;
                    border-top: 1px solid var(--border-subtle);
                }
                .spotlight-btns-left {
                    display: flex;
                    align-items: center;
                    gap: 10px;
                }
                .btn-resume {
                    background: var(--pn-red);
                    color: #FFFFFF;
                    border: none;
                    border-radius: 20px;
                    padding: 9px 20px;
                    font-weight: 700;
                    font-size: 13.5px;
                    cursor: pointer;
                    display: flex;
                    align-items: center;
                    gap: 8px;
                    box-shadow: 0 2px 8px rgba(225, 29, 72, 0.3);
                }
                .btn-start-over {
                    background: #FFFFFF;
                    color: var(--text-heading);
                    border: 1px solid var(--border-light);
                    border-radius: 20px;
                    padding: 9px 16px;
                    font-weight: 600;
                    font-size: 13px;
                    cursor: pointer;
                }
                .spotlight-icons-right {
                    display: flex;
                    gap: 8px;
                }

                /* Right: Up Next in Queue */
                .queue-panel {
                    background: #FFFFFF;
                    border: 1px solid var(--border-light);
                    border-radius: 18px;
                    padding: 20px 22px;
                    box-shadow: var(--shadow-sm);
                    display: flex;
                    flex-direction: column;
                    gap: 14px;
                }
                .section-header-row {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                }
                .section-lead-title {
                    display: flex;
                    align-items: center;
                    gap: 8px;
                    font-size: 13px;
                    font-weight: 800;
                    letter-spacing: 0.6px;
                    text-transform: uppercase;
                    color: var(--text-heading);
                }
                .red-square-indicator {
                    width: 7px;
                    height: 7px;
                    background: var(--pn-red);
                    border-radius: 2px;
                }
                .section-sub-action {
                    font-family: var(--font-mono);
                    font-size: 11px;
                    font-weight: 700;
                    color: var(--text-muted);
                    text-decoration: none;
                    cursor: pointer;
                    display: flex;
                    align-items: center;
                    gap: 4px;
                }
                .section-sub-action:hover { color: var(--pn-red); }
                .queue-list {
                    display: flex;
                    flex-direction: column;
                    gap: 8px;
                    flex: 1;
                }
                .queue-row {
                    display: flex;
                    align-items: center;
                    gap: 12px;
                    padding: 10px 12px;
                    background: var(--bg-canvas);
                    border: 1px solid var(--border-subtle);
                    border-radius: 10px;
                    cursor: pointer;
                    transition: all 0.15s ease;
                }
                .queue-row:hover {
                    background: #FFFFFF;
                    border-color: var(--border-light);
                    box-shadow: var(--shadow-sm);
                    transform: translateX(2px);
                }
                .queue-row-active {
                    background: #FFF1F2;
                    border-color: #FECDD3;
                }
                .q-num {
                    font-family: var(--font-mono);
                    font-size: 12px;
                    font-weight: 700;
                    color: var(--pn-red);
                    width: 22px;
                }
                .q-info {
                    flex: 1;
                    display: flex;
                    flex-direction: column;
                    gap: 2px;
                }
                .q-title-row {
                    display: flex;
                    align-items: center;
                    gap: 8px;
                }
                .q-title {
                    font-size: 13.5px;
                    font-weight: 700;
                    color: var(--text-heading);
                }
                .q-playing-badge {
                    font-size: 9px;
                    font-weight: 800;
                    background: var(--pn-red);
                    color: #FFFFFF;
                    padding: 1px 5px;
                    border-radius: 3px;
                }
                .q-meta {
                    font-size: 11.5px;
                    color: var(--text-muted);
                }
                .q-action {
                    color: var(--text-light);
                    font-size: 13px;
                }
                .add-queue-btn {
                    background: #FFFFFF;
                    border: 1px dashed var(--border-light);
                    border-radius: 10px;
                    padding: 10px;
                    font-weight: 700;
                    font-size: 12.5px;
                    color: var(--text-muted);
                    cursor: pointer;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    gap: 6px;
                    transition: all 0.15s ease;
                }
                .add-queue-btn:hover {
                    border-color: var(--pn-red);
                    color: var(--pn-red);
                    background: var(--pn-red-soft);
                }

                /* 3. Watch Together Lounge (SYNC PROTOCOL V3) */
                .sync-section-wrap {
                    background: #FFFFFF;
                    border: 1px solid var(--border-light);
                    border-radius: 18px;
                    padding: 22px 24px;
                    box-shadow: var(--shadow-sm);
                    display: flex;
                    flex-direction: column;
                    gap: 16px;
                }
                .sync-header-row {
                    display: flex;
                    align-items: flex-start;
                    justify-content: space-between;
                    gap: 16px;
                    flex-wrap: wrap;
                }
                .sync-lead-col {
                    display: flex;
                    flex-direction: column;
                    gap: 4px;
                }
                .sync-protocol-tag {
                    display: flex;
                    align-items: center;
                    gap: 8px;
                    font-family: var(--font-mono);
                    font-size: 11px;
                    font-weight: 700;
                    color: var(--pn-red);
                    letter-spacing: 0.6px;
                }
                .sync-h2 {
                    font-size: 22px;
                    font-weight: 800;
                    letter-spacing: -0.4px;
                }
                .sync-sub {
                    font-size: 13px;
                    color: var(--text-muted);
                }
                .btn-start-party {
                    background: #FFFFFF;
                    border: 1px solid var(--border-light);
                    border-radius: 20px;
                    padding: 8px 18px;
                    font-weight: 700;
                    font-size: 13px;
                    color: var(--text-heading);
                    cursor: pointer;
                    box-shadow: var(--shadow-sm);
                    display: flex;
                    align-items: center;
                    gap: 6px;
                }
                .sync-panels-grid {
                    display: grid;
                    grid-template-columns: 1fr 1fr;
                    gap: 20px;
                }
                .room-info-card {
                    background: var(--bg-canvas);
                    border: 1px solid var(--border-light);
                    border-radius: 14px;
                    padding: 18px 20px;
                    display: flex;
                    flex-direction: column;
                    gap: 14px;
                }
                .room-name-row {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                }
                .room-name-title {
                    font-size: 16px;
                    font-weight: 800;
                    display: flex;
                    align-items: center;
                    gap: 8px;
                }
                .green-dot {
                    width: 7px;
                    height: 7px;
                    border-radius: 50%;
                    background: #22C55E;
                }
                .zero-delay-pill {
                    font-family: var(--font-mono);
                    font-size: 10px;
                    font-weight: 700;
                    background: #FEF2F2;
                    color: var(--pn-red);
                    border: 1px solid #FECACA;
                    padding: 2px 7px;
                    border-radius: 4px;
                }
                .room-specs-table {
                    display: grid;
                    grid-template-columns: 1fr 1fr 1fr;
                    gap: 10px;
                    font-family: var(--font-mono);
                }
                .room-spec-col {
                    display: flex;
                    flex-direction: column;
                    gap: 2px;
                }
                .spec-label {
                    font-size: 10px;
                    color: var(--text-light);
                    text-transform: uppercase;
                }
                .spec-val {
                    font-size: 12.5px;
                    font-weight: 700;
                    color: var(--text-heading);
                }
                .audio-lossless-row {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    background: #FFFFFF;
                    border: 1px solid var(--border-light);
                    border-radius: 8px;
                    padding: 8px 12px;
                    font-size: 12px;
                    font-weight: 600;
                }
                .badge-flac {
                    font-family: var(--font-mono);
                    font-size: 10px;
                    font-weight: 800;
                    background: var(--teal-soft);
                    color: var(--teal-accent);
                    padding: 2px 6px;
                    border-radius: 4px;
                }
                .connected-avatars-row {
                    display: flex;
                    align-items: center;
                    gap: 10px;
                }
                .avatar-stack {
                    display: flex;
                    align-items: center;
                }
                .stacked-avatar {
                    width: 26px;
                    height: 26px;
                    border-radius: 50%;
                    border: 2px solid #FFFFFF;
                    color: #FFFFFF;
                    font-weight: 800;
                    font-size: 10.5px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    margin-left: -6px;
                }
                .stacked-avatar:first-child { margin-left: 0; }
                .av-k { background: #8B5CF6; }
                .av-s { background: #EC4899; }
                .av-m { background: #F59E0B; }
                .members-label {
                    font-size: 12px;
                    color: var(--text-muted);
                }
                .btn-join-sync {
                    background: var(--pn-red);
                    color: #FFFFFF;
                    border: none;
                    border-radius: 8px;
                    padding: 10px;
                    font-weight: 800;
                    font-size: 13.5px;
                    letter-spacing: 0.5px;
                    cursor: pointer;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    gap: 8px;
                }

                /* Sync Chat & Reactions */
                .chat-reactions-card {
                    background: var(--bg-canvas);
                    border: 1px solid var(--border-light);
                    border-radius: 14px;
                    padding: 18px 20px;
                    display: flex;
                    flex-direction: column;
                    justify-content: space-between;
                    gap: 12px;
                }
                .chat-head-row {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                }
                .chat-title {
                    font-family: var(--font-mono);
                    font-size: 11px;
                    font-weight: 800;
                    color: var(--text-muted);
                    letter-spacing: 0.6px;
                }
                .chat-feed {
                    display: flex;
                    flex-direction: column;
                    gap: 10px;
                    max-height: 145px;
                    overflow-y: auto;
                }
                .chat-msg {
                    display: flex;
                    flex-direction: column;
                    gap: 2px;
                    background: #FFFFFF;
                    border: 1px solid var(--border-subtle);
                    border-radius: 8px;
                    padding: 6px 10px;
                }
                .msg-meta-row {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                }
                .msg-user {
                    font-size: 11px;
                    font-weight: 800;
                    color: var(--text-heading);
                }
                .msg-time {
                    font-family: var(--font-mono);
                    font-size: 10px;
                    color: var(--text-light);
                }
                .msg-text {
                    font-size: 12px;
                    color: var(--text-muted);
                }
                .chat-input-row {
                    display: flex;
                    gap: 8px;
                }
                .chat-input-box {
                    flex: 1;
                    background: #FFFFFF;
                    border: 1px solid var(--border-light);
                    border-radius: 8px;
                    padding: 8px 12px;
                    font-size: 12px;
                    font-family: inherit;
                    outline: none;
                }
                .btn-chat-send {
                    background: var(--pn-red);
                    color: #FFFFFF;
                    border: none;
                    border-radius: 8px;
                    padding: 0 16px;
                    font-weight: 800;
                    font-size: 12px;
                    cursor: pointer;
                }

                /* 4. Movie Cards Shelves (Recently Added) */
                .shelf-section {
                    display: flex;
                    flex-direction: column;
                    gap: 16px;
                }
                .shelf-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
                    gap: 18px;
                }
                .movie-card {
                    background: #FFFFFF;
                    border: 1px solid var(--border-light);
                    border-radius: 12px;
                    overflow: hidden;
                    box-shadow: var(--shadow-sm);
                    cursor: pointer;
                    display: flex;
                    flex-direction: column;
                    transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
                }
                .movie-card:hover {
                    transform: translateY(-4px);
                    box-shadow: var(--shadow-md);
                    border-color: var(--pn-red);
                }
                .poster-container {
                    position: relative;
                    width: 100%;
                    aspect-ratio: 2 / 3;
                    background: #1E293B;
                    overflow: hidden;
                }
                .poster-img {
                    width: 100%;
                    height: 100%;
                    object-fit: cover;
                    transition: transform 0.3s ease;
                }
                .movie-card:hover .poster-img {
                    transform: scale(1.05);
                }
                .card-badge-top-left {
                    position: absolute;
                    top: 8px;
                    left: 8px;
                    background: rgba(15, 23, 42, 0.75);
                    backdrop-filter: blur(4px);
                    color: #FFFFFF;
                    font-family: var(--font-mono);
                    font-size: 9.5px;
                    font-weight: 800;
                    padding: 2px 6px;
                    border-radius: 4px;
                }
                .card-badge-top-right {
                    position: absolute;
                    top: 8px;
                    right: 8px;
                    background: var(--pn-red);
                    color: #FFFFFF;
                    font-size: 10px;
                    font-weight: 800;
                    padding: 2px 6px;
                    border-radius: 4px;
                }
                .poster-hover-overlay {
                    position: absolute;
                    inset: 0;
                    background: rgba(15, 23, 42, 0.4);
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    opacity: 0;
                    transition: opacity 0.2s ease;
                }
                .movie-card:hover .poster-hover-overlay {
                    opacity: 1;
                }
                .play-circle-btn {
                    width: 42px;
                    height: 42px;
                    border-radius: 50%;
                    background: var(--pn-red);
                    color: #FFFFFF;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-size: 16px;
                    box-shadow: 0 4px 14px rgba(225, 29, 72, 0.4);
                }
                .movie-info {
                    padding: 12px 14px;
                    display: flex;
                    flex-direction: column;
                    gap: 4px;
                    flex: 1;
                }
                .movie-title {
                    font-size: 13.5px;
                    font-weight: 700;
                    color: var(--text-heading);
                    white-space: nowrap;
                    overflow: hidden;
                    text-overflow: ellipsis;
                }
                .movie-meta {
                    font-size: 11px;
                    color: var(--text-muted);
                    margin-bottom: 8px;
                }
                .watch-btn {
                    margin-top: auto;
                    width: 100%;
                    background: var(--bg-subtle);
                    border: 1px solid var(--border-light);
                    color: var(--text-heading);
                    border-radius: 6px;
                    padding: 6px 0;
                    font-size: 11.5px;
                    font-weight: 800;
                    letter-spacing: 0.5px;
                    cursor: pointer;
                    transition: all 0.15s ease;
                }
                .watch-btn:hover {
                    background: var(--pn-red);
                    color: #FFFFFF;
                    border-color: var(--pn-red);
                }

                /* 5. Recommended by Friends */
                .reviews-grid {
                    display: grid;
                    grid-template-columns: repeat(3, 1fr);
                    gap: 20px;
                }
                .review-card {
                    background: #FFFFFF;
                    border: 1px solid var(--border-light);
                    border-radius: 14px;
                    padding: 18px 20px;
                    display: flex;
                    flex-direction: column;
                    gap: 12px;
                    box-shadow: var(--shadow-sm);
                }
                .review-user-row {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                }
                .user-badge-left {
                    display: flex;
                    align-items: center;
                    gap: 10px;
                }
                .user-letter-av {
                    width: 30px;
                    height: 30px;
                    border-radius: 50%;
                    color: #FFFFFF;
                    font-weight: 800;
                    font-size: 13px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                }
                .reviewer-name {
                    font-size: 13px;
                    font-weight: 800;
                    color: var(--text-heading);
                }
                .review-time {
                    font-size: 10.5px;
                    color: var(--text-light);
                }
                .score-pill {
                    font-family: var(--font-mono);
                    font-size: 11px;
                    font-weight: 800;
                    background: #FFF1F2;
                    color: var(--pn-red);
                    padding: 2px 7px;
                    border-radius: 4px;
                }
                .film-thumb-row {
                    display: flex;
                    align-items: center;
                    gap: 12px;
                    background: var(--bg-canvas);
                    border: 1px solid var(--border-subtle);
                    border-radius: 8px;
                    padding: 8px 10px;
                }
                .film-mini-thumb {
                    width: 38px;
                    height: 52px;
                    border-radius: 4px;
                    object-fit: cover;
                }
                .film-mini-title {
                    font-size: 13px;
                    font-weight: 700;
                    color: var(--text-heading);
                }
                .film-mini-sub {
                    font-size: 11px;
                    color: var(--text-muted);
                }
                .review-quote {
                    font-size: 12.5px;
                    font-style: italic;
                    color: var(--text-muted);
                    line-height: 1.5;
                }
                .review-actions-row {
                    display: flex;
                    gap: 8px;
                    margin-top: auto;
                }
                .btn-watch-now {
                    flex: 1;
                    background: var(--pn-red);
                    color: #FFFFFF;
                    border: none;
                    border-radius: 6px;
                    padding: 7px;
                    font-weight: 800;
                    font-size: 11px;
                    letter-spacing: 0.5px;
                    cursor: pointer;
                }
                .btn-plus-queue {
                    background: #FFFFFF;
                    border: 1px solid var(--border-light);
                    color: var(--text-heading);
                    border-radius: 6px;
                    padding: 7px 12px;
                    font-weight: 700;
                    font-size: 11px;
                    cursor: pointer;
                }

                /* 6. Popular & Trending */
                .trending-grid {
                    display: grid;
                    grid-template-columns: repeat(4, 1fr);
                    gap: 18px;
                }
                .trending-card {
                    background: #FFFFFF;
                    border: 1px solid var(--border-light);
                    border-radius: 12px;
                    overflow: hidden;
                    box-shadow: var(--shadow-sm);
                    display: flex;
                    flex-direction: column;
                    cursor: pointer;
                    transition: transform 0.2s ease;
                }
                .trending-card:hover {
                    transform: translateY(-3px);
                }
                .trending-thumb-wrap {
                    position: relative;
                    height: 120px;
                    background: #0F172A;
                }
                .trending-thumb-img {
                    width: 100%;
                    height: 100%;
                    object-fit: cover;
                }
                .trending-rank-badge {
                    position: absolute;
                    top: 8px;
                    left: 8px;
                    background: var(--pn-red);
                    color: #FFFFFF;
                    font-size: 10px;
                    font-weight: 800;
                    padding: 2px 6px;
                    border-radius: 3px;
                }
                .trending-dur-badge {
                    position: absolute;
                    bottom: 8px;
                    right: 8px;
                    background: rgba(15, 23, 42, 0.75);
                    color: #FFFFFF;
                    font-family: var(--font-mono);
                    font-size: 9.5px;
                    padding: 2px 5px;
                    border-radius: 3px;
                }
                .trending-body {
                    padding: 12px 14px;
                    display: flex;
                    flex-direction: column;
                    gap: 4px;
                }
                .trending-studio {
                    font-family: var(--font-mono);
                    font-size: 9.5px;
                    color: var(--text-light);
                    text-transform: uppercase;
                }
                .trending-title {
                    font-size: 13.5px;
                    font-weight: 700;
                    color: var(--text-heading);
                    white-space: nowrap;
                    overflow: hidden;
                    text-overflow: ellipsis;
                }
                .trending-footer {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    margin-top: 6px;
                }
                .trending-rating {
                    font-family: var(--font-mono);
                    font-size: 11px;
                    font-weight: 700;
                    color: var(--pn-red);
                }
                .trending-quick-play {
                    font-size: 11.5px;
                    font-weight: 700;
                    color: var(--text-muted);
                }

                /* 7. Split: Shared with You & Offline Storage */
                .split-shares-storage {
                    display: grid;
                    grid-template-columns: 1fr 1fr;
                    gap: 24px;
                }
                .shares-card, .storage-card {
                    background: #FFFFFF;
                    border: 1px solid var(--border-light);
                    border-radius: 16px;
                    padding: 20px 22px;
                    box-shadow: var(--shadow-sm);
                    display: flex;
                    flex-direction: column;
                    gap: 14px;
                }
                .share-items-list {
                    display: flex;
                    flex-direction: column;
                    gap: 8px;
                }
                .share-item-row {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    background: var(--bg-canvas);
                    border: 1px solid var(--border-subtle);
                    border-radius: 8px;
                    padding: 8px 12px;
                }
                .share-item-left {
                    display: flex;
                    align-items: center;
                    gap: 10px;
                }
                .share-icon { color: var(--pn-red); font-size: 14px; }
                .share-title { font-size: 13px; font-weight: 700; }
                .share-sub { font-size: 10.5px; color: var(--text-muted); }
                .btn-stream-tag {
                    font-size: 11px;
                    font-weight: 700;
                    color: var(--text-muted);
                    background: #FFFFFF;
                    border: 1px solid var(--border-light);
                    border-radius: 4px;
                    padding: 3px 8px;
                    cursor: pointer;
                }

                /* Offline Cache Panel */
                .storage-meter-box {
                    display: flex;
                    flex-direction: column;
                    gap: 6px;
                }
                .meter-info-row {
                    display: flex;
                    justify-content: space-between;
                    font-family: var(--font-mono);
                    font-size: 11px;
                    color: var(--text-muted);
                }
                .meter-track {
                    height: 6px;
                    background: var(--border-light);
                    border-radius: 3px;
                    overflow: hidden;
                }
                .meter-fill {
                    height: 100%;
                    background: var(--pn-red);
                    border-radius: 3px;
                }
                .offline-items-list {
                    display: flex;
                    flex-direction: column;
                    gap: 6px;
                }
                .offline-item-row {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    font-size: 12.5px;
                    font-weight: 600;
                    padding: 6px 4px;
                }
                .ready-badge {
                    font-family: var(--font-mono);
                    font-size: 10px;
                    font-weight: 800;
                    color: #22C55E;
                    display: flex;
                    align-items: center;
                    gap: 4px;
                }
                .btn-travel-mode {
                    background: var(--bg-canvas);
                    border: 1px solid var(--border-light);
                    border-radius: 8px;
                    padding: 8px;
                    font-size: 12px;
                    font-weight: 700;
                    color: var(--text-heading);
                    cursor: pointer;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    gap: 6px;
                }

                /* Telemetry & System Footer */
                .system-footer-wrap {
                    display: flex;
                    flex-direction: column;
                    gap: 12px;
                    margin-top: 20px;
                }
                .footer-meta-line {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    font-size: 11.5px;
                    color: var(--text-muted);
                }
                .footer-links {
                    display: flex;
                    gap: 16px;
                }
                .footer-links a {
                    color: var(--text-muted);
                    text-decoration: none;
                }
                .footer-links a:hover { color: var(--pn-red); }
                .telemetry-card {
                    background: #FFFFFF;
                    border: 1px solid var(--border-light);
                    border-radius: 12px;
                    padding: 16px 20px;
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    gap: 20px;
                    flex-wrap: wrap;
                }
                .telemetry-left {
                    display: flex;
                    align-items: center;
                    gap: 12px;
                }
                .telemetry-title {
                    font-size: 13.5px;
                    font-weight: 800;
                }
                .telemetry-desc {
                    font-size: 11.5px;
                    color: var(--text-muted);
                }
                .telemetry-stats-row {
                    display: flex;
                    align-items: center;
                    gap: 18px;
                    font-family: var(--font-mono);
                    font-size: 11px;
                    color: var(--text-muted);
                }
                .stat-highlight {
                    color: var(--pn-red);
                    font-weight: 700;
                }

                /* Mobile Status Pill (Seen on Phone Design) */
                .mobile-system-status-pill {
                    display: none;
                    background: #0F172A;
                    color: #FFFFFF;
                    border-radius: 14px;
                    padding: 16px 18px;
                    flex-direction: column;
                    gap: 6px;
                }
                .mobile-status-top-row {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    font-family: var(--font-mono);
                    font-size: 11px;
                }
                .mobile-status-ok {
                    display: flex;
                    align-items: center;
                    gap: 6px;
                    color: #22C55E;
                    font-weight: 700;
                }
                .mobile-status-mid {
                    font-family: var(--font-mono);
                    font-size: 12px;
                    font-weight: 700;
                    color: #E2E8F0;
                }
                .mobile-status-bottom {
                    font-family: var(--font-mono);
                    font-size: 10px;
                    color: #94A3B8;
                }

                /* Bottom Navigation Bar (Phone Only) */
                .mobile-bottom-nav {
                    display: none;
                    position: fixed;
                    bottom: 0;
                    left: 0;
                    right: 0;
                    background: #FFFFFF;
                    border-top: 1px solid var(--border-light);
                    padding: 8px 12px 14px;
                    z-index: 60;
                    justify-content: space-around;
                    align-items: center;
                }
                .bottom-nav-item {
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    gap: 4px;
                    font-size: 10.5px;
                    font-weight: 700;
                    color: var(--text-muted);
                    text-decoration: none;
                    cursor: pointer;
                    background: none;
                    border: none;
                }
                .bottom-nav-item.active {
                    color: var(--pn-red);
                }
                .bottom-nav-icon {
                    font-size: 18px;
                }

                /* Direct Video Player Modal */
                #direct-player-container {
                    display: none;
                    position: fixed;
                    inset: 0;
                    background: #000000;
                    z-index: 1000;
                }
                #video-player {
                    width: 100%;
                    height: 100%;
                    outline: none;
                }
                .player-osd-header {
                    position: absolute;
                    top: 0;
                    left: 0;
                    right: 0;
                    padding: 20px 24px;
                    background: linear-gradient(180deg, rgba(0, 0, 0, 0.85) 0%, transparent 100%);
                    display: flex;
                    align-items: center;
                    gap: 16px;
                    color: #FFFFFF;
                    z-index: 10;
                    transition: opacity 0.3s ease;
                }
                .player-osd-header.hidden {
                    opacity: 0;
                    pointer-events: none;
                }
                .player-back-btn {
                    background: rgba(255, 255, 255, 0.15);
                    border: 1px solid rgba(255, 255, 255, 0.25);
                    border-radius: 50%;
                    width: 44px;
                    height: 44px;
                    color: #FFFFFF;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    cursor: pointer;
                }
                .player-title-main {
                    font-size: 18px;
                    font-weight: 800;
                }
                .player-title-sub {
                    font-size: 12px;
                    color: #94A3B8;
                }
                .player-osd-actions {
                    display: flex;
                    align-items: center;
                    gap: 8px;
                    margin-left: auto;
                    flex-shrink: 0;
                }
                .player-ext-pill {
                    background: rgba(255, 255, 255, 0.16);
                    border: 1px solid rgba(255, 255, 255, 0.28);
                    backdrop-filter: blur(8px);
                    -webkit-backdrop-filter: blur(8px);
                    color: #FFFFFF;
                    border-radius: 20px;
                    padding: 6px 12px;
                    font-size: 11.5px;
                    font-weight: 700;
                    cursor: pointer;
                    display: flex;
                    align-items: center;
                    gap: 5px;
                    white-space: nowrap;
                    transition: background 0.2s, transform 0.1s;
                }
                .player-ext-pill:hover {
                    background: rgba(225, 29, 72, 0.85);
                    border-color: var(--pn-red);
                }
                .player-fallback-modal {
                    position: absolute;
                    inset: 0;
                    background: rgba(10, 15, 29, 0.94);
                    backdrop-filter: blur(20px);
                    -webkit-backdrop-filter: blur(20px);
                    z-index: 50;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    padding: 20px 16px;
                    overflow-y: auto;
                    box-sizing: border-box;
                }
                .player-fallback-card {
                    background: #111827;
                    border: 1px solid rgba(255, 255, 255, 0.12);
                    border-radius: 20px;
                    padding: 24px 20px;
                    max-width: 440px;
                    width: 100%;
                    color: #F8FAFC;
                    box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.7);
                    text-align: center;
                    box-sizing: border-box;
                }
                .fallback-badge {
                    display: inline-block;
                    background: rgba(225, 29, 72, 0.2);
                    border: 1px solid rgba(225, 29, 72, 0.4);
                    color: #FB7185;
                    font-size: 11px;
                    font-weight: 800;
                    padding: 4px 10px;
                    border-radius: 9999px;
                    text-transform: uppercase;
                    letter-spacing: 0.5px;
                    margin-bottom: 12px;
                }
                .fallback-title {
                    font-size: 18px;
                    font-weight: 800;
                    margin: 0 0 8px 0;
                    color: #FFFFFF;
                }
                .fallback-desc {
                    font-size: 13px;
                    line-height: 1.5;
                    color: #94A3B8;
                    margin: 0 0 20px 0;
                }
                .fallback-actions-grid {
                    display: flex;
                    flex-direction: column;
                    gap: 10px;
                }
                .fallback-btn {
                    display: flex;
                    align-items: center;
                    gap: 12px;
                    background: #1F2937;
                    border: 1px solid rgba(255, 255, 255, 0.1);
                    border-radius: 12px;
                    padding: 12px 14px;
                    color: #FFFFFF;
                    cursor: pointer;
                    text-align: left;
                    transition: background 0.15s, border-color 0.15s;
                    width: 100%;
                    box-sizing: border-box;
                }
                .fallback-btn:hover {
                    background: #374151;
                    border-color: rgba(255, 255, 255, 0.3);
                }
                .fallback-btn-vlc {
                    background: linear-gradient(135deg, rgba(234, 88, 12, 0.25) 0%, #1F2937 100%);
                    border-color: rgba(234, 88, 12, 0.4);
                }
                .fallback-btn-infuse {
                    background: linear-gradient(135deg, rgba(225, 29, 72, 0.25) 0%, #1F2937 100%);
                    border-color: rgba(225, 29, 72, 0.4);
                }
                .fallback-btn-outplayer {
                    background: linear-gradient(135deg, rgba(14, 165, 233, 0.25) 0%, #1F2937 100%);
                    border-color: rgba(14, 165, 233, 0.4);
                }
                .fb-btn-icon {
                    font-size: 24px;
                    flex-shrink: 0;
                }
                .fb-btn-text {
                    flex: 1;
                    min-width: 0;
                }
                .fb-btn-primary {
                    font-size: 14px;
                    font-weight: 700;
                    color: #FFFFFF;
                }
                .fb-btn-sub {
                    font-size: 11px;
                    color: #94A3B8;
                }
                .fallback-footer {
                    margin-top: 16px;
                    display: flex;
                    justify-content: center;
                }
                .fallback-btn-dismiss {
                    background: transparent;
                    border: none;
                    color: #64748B;
                    font-size: 12px;
                    cursor: pointer;
                    padding: 6px 12px;
                }
                .fallback-btn-dismiss:hover {
                    color: #94A3B8;
                }

                .btn-get-app {
                    display: inline-flex;
                    align-items: center;
                    gap: 6px;
                    background: linear-gradient(135deg, var(--pn-red) 0%, #be123c 100%);
                    color: #FFFFFF;
                    border: none;
                    border-radius: 20px;
                    padding: 7px 14px;
                    font-size: 11.5px;
                    font-weight: 800;
                    letter-spacing: 0.4px;
                    cursor: pointer;
                    box-shadow: 0 4px 12px rgba(225, 29, 72, 0.3);
                    transition: transform 0.15s, box-shadow 0.15s;
                    white-space: nowrap;
                }
                .btn-get-app:hover {
                    transform: translateY(-1px);
                    box-shadow: 0 6px 16px rgba(225, 29, 72, 0.45);
                }
                .mobile-get-app-btn {
                    display: flex;
                    align-items: center;
                    gap: 4px;
                    background: var(--pn-red);
                    color: #FFFFFF;
                    border: none;
                    border-radius: 12px;
                    padding: 5px 10px;
                    font-size: 11px;
                    font-weight: 800;
                    cursor: pointer;
                    white-space: nowrap;
                }

                /* RESPONSIVE BREAKPOINTS (Mobile Adaptations matching Image 2) */
                @media (max-width: 900px) {
                    .spotlight-queue-grid {
                        grid-template-columns: 1fr;
                    }
                    .sync-panels-grid {
                        grid-template-columns: 1fr;
                    }
                    .reviews-grid {
                        grid-template-columns: 1fr;
                    }
                    .trending-grid {
                        grid-template-columns: 1fr 1fr;
                    }
                    .split-shares-storage {
                        grid-template-columns: 1fr;
                    }
                }

                @media (max-width: 768px) {
                    header.site-header { display: none; }
                    .mobile-header-bar { display: flex; }
                    .mobile-bottom-nav { display: flex; }
                    .mobile-system-status-pill { display: flex; }
                    .telemetry-card { display: none; }

                    main.page-canvas {
                        padding: 16px 14px 90px;
                        gap: 22px;
                    }

                    .shares-card, .storage-card {
                        padding: 16px 12px;
                        border-radius: 14px;
                        box-sizing: border-box;
                        width: 100%;
                        overflow: hidden;
                    }
                    .offline-items-list {
                        width: 100%;
                        min-width: 0;
                        box-sizing: border-box;
                    }
                    .section-header-row {
                        flex-wrap: wrap;
                        gap: 6px;
                    }
                    .section-lead-title {
                        flex-wrap: wrap;
                        font-size: 12px;
                    }

                    .hub-title-actions-row {
                        flex-direction: column;
                        align-items: flex-start;
                    }
                    .hub-action-btns {
                        width: 100%;
                    }
                    .play-all-pill-btn, .shuffle-pill-btn {
                        flex: 1;
                        justify-content: center;
                    }

                    .shelf-grid {
                        display: flex;
                        overflow-x: auto;
                        scroll-snap-type: x mandatory;
                        padding-bottom: 8px;
                        margin: 0 -14px;
                        padding-left: 14px;
                        padding-right: 14px;
                        gap: 14px;
                        scrollbar-width: none;
                    }
                    .shelf-grid::-webkit-scrollbar { display: none; }
                    .movie-card {
                        min-width: 150px;
                        max-width: 150px;
                        scroll-snap-align: start;
                        flex-shrink: 0;
                    }

                    .trending-grid {
                        display: flex;
                        overflow-x: auto;
                        padding-bottom: 8px;
                        margin: 0 -14px;
                        padding-left: 14px;
                        padding-right: 14px;
                        gap: 14px;
                        scrollbar-width: none;
                    }
                    .trending-grid::-webkit-scrollbar { display: none; }
                    .trending-card {
                        min-width: 220px;
                        max-width: 220px;
                        flex-shrink: 0;
                    }
                }
            </style>
        </head>
        <body>
            <!-- Desktop / TV Header Navigation Bar -->
            <header class="site-header">
                <div class="nav-container">
                    <!-- Brand & Logo -->
                    <div class="brand-wrap" onclick="window.scrollTo({top:0, behavior:'smooth'})">
                        <div class="logo-squircle">
                            <!-- SVG Logo Exact Match to Stitch Image -->
                            <svg width="34" height="34" viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg">
                                <rect width="100" height="100" rx="26" fill="#E11D48"/>
                                <rect x="16" y="24" width="10" height="52" rx="5" fill="#FFFFFF"/>
                                <circle cx="21" cy="30" r="2.5" fill="#E11D48"/>
                                <circle cx="21" cy="70" r="2.5" fill="#E11D48"/>
                                <rect x="22" y="38" width="56" height="24" rx="8" fill="#1E293B"/>
                                <polygon points="44,44 44,56 56,50" fill="#FFFFFF"/>
                                <circle cx="67" cy="50" r="3.5" fill="#2DD4BF"/>
                                <rect x="26" y="66" width="46" height="10" rx="4" fill="#FFFFFF"/>
                                <rect x="34" y="74" width="4" height="10" rx="2" fill="#FFFFFF"/>
                                <rect x="46" y="74" width="5" height="15" rx="2.5" fill="#F59E0B"/>
                                <rect x="58" y="74" width="4" height="10" rx="2" fill="#FFFFFF"/>
                            </svg>
                        </div>
                        <div class="brand-meta">
                            <div class="brand-title-row">
                                <span class="pn-part">PN-NODE</span>
                                <span class="pn-cross">×</span>
                                <span class="jf-part">JELLYFIN</span>
                            </div>
                            <div class="brand-sub">SELF-HOSTED MEDIA SERVER &amp; CLIENT</div>
                            <div class="brand-pills-row">
                                <span class="pill-tag pill-hl">HOMELAB</span>
                                <span class="pill-tag pill-tc">TRANSCODE</span>
                                <span class="pill-tag pill-dp">DIRECT</span>
                            </div>
                        </div>
                    </div>

                    <!-- Navigation Tabs -->
                    <nav class="nav-tabs">
                        <button class="nav-tab-btn active" onclick="switchNavTab(this, 'movies')">Movies</button>
                        <button class="nav-tab-btn" onclick="switchNavTab(this, 'shows')">Shows</button>
                        <button class="nav-tab-btn" onclick="switchNavTab(this, 'playlists')">Playlists</button>
                        <button class="nav-tab-btn" onclick="switchNavTab(this, 'party')">Watch Party</button>
                    </nav>

                    <!-- Search Bar & Actions -->
                    <div class="nav-right-actions">
                        <button class="btn-get-app" onclick="openAppModal()" title="Download PocketNode Android TV & Mobile App (APK)">
                            <span>📲 GET APP</span>
                        </button>
                        <div class="search-box-pill">
                            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="#64748B" stroke-width="2.5"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
                            <input id="main-search-input" class="search-input-field" type="text" placeholder="Search movies, series, directors..." oninput="filterMedia(this.value)" />
                            <span class="kbd-badge">CTRL+K</span>
                        </div>
                        <div class="icon-action-btn" title="Cast to Screen (AirPlay / Chromecast)">
                            <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M2 16.1A5 5 0 0 1 5.9 20M2 12.05A9 9 0 0 1 9.95 20M2 8V6a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-6M2 20h.01"/></svg>
                        </div>
                        <div class="icon-action-btn" title="Notifications" onclick="triggerScan(event)">
                            <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9M13.73 21a2 2 0 0 1-3.46 0"/></svg>
                            <div class="btn-dot"></div>
                        </div>
                        <div class="user-avatar-pill">
                            <div class="avatar-circle">
                                <span>W</span>
                                <div class="avatar-online-dot"></div>
                            </div>
                            <span>Watchlist</span>
                        </div>
                    </div>
                </div>
            </header>

            <!-- Mobile App Top Bar -->
            <div class="mobile-header-bar">
                <div class="mobile-logo-title-col">
                    <div class="logo-squircle" style="width:34px; height:34px; border-radius:10px;">
                        <svg width="24" height="24" viewBox="0 0 100 100" fill="none">
                            <rect width="100" height="100" rx="26" fill="#E11D48"/>
                            <rect x="22" y="38" width="56" height="24" rx="8" fill="#1E293B"/>
                            <polygon points="44,44 44,56 56,50" fill="#FFFFFF"/>
                            <circle cx="67" cy="50" r="3.5" fill="#2DD4BF"/>
                        </svg>
                    </div>
                    <div>
                        <div class="mobile-sub-red">JELLYFIN / PN</div>
                        <div class="mobile-title-text">MOVIES</div>
                    </div>
                </div>
                <div style="display:flex; align-items:center; gap:8px;">
                    <button class="mobile-get-app-btn" onclick="openAppModal()" title="Download PocketNode Android App (APK)">
                        <span>📲 APK</span>
                    </button>
                    <div class="icon-action-btn" onclick="document.getElementById('main-search-input').focus()">
                        <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
                    </div>
                    <div class="avatar-circle" style="width:32px; height:32px;">
                        <span>JH</span>
                    </div>
                </div>
            </div>

            <!-- Page Canvas -->
            <main class="page-canvas">

                <!-- 1. Curated Hub Banner -->
                <section class="curated-hub-header">
                    <div class="curated-meta-row">
                        <div class="curated-badges-left">
                            <span class="curated-tag-red">HOMELAB CINEMA</span>
                            <span class="curated-tag-outline">${allVideoCount} TITLES</span>
                            <span class="curated-tag-outline">DIRECT PLAY</span>
                            <span class="curated-tag-outline">LOSSLESS</span>
                        </div>
                        <div class="curated-tech-right">
                            <span class="curated-tech-item">● NODE: ACTIVE</span>
                            <span class="curated-tech-item">● FASTCD: READY</span>
                        </div>
                    </div>

                    <div class="hub-title-actions-row">
                        <div class="hub-title-col">
                            <h1 class="hub-main-h1">PocketNode Personal Cinema</h1>
                            <p class="hub-sub-text">High-fidelity lossless personal streaming node. Zero re-encoding for local sandbox, direct sequential playback for torrent swarms, and telegram cloud library.</p>
                        </div>
                        <div class="hub-action-btns">
                            <button class="play-all-pill-btn" onclick="playMedia('$firstStreamUrl', '$firstTitle')">
                                <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>
                                <span>Play All</span>
                            </button>
                            <button class="shuffle-pill-btn" onclick="shufflePlay()">
                                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="16 3 21 3 21 8"/><line x1="4" y1="20" x2="21" y2="3"/><polyline points="21 16 21 21 16 21"/><line x1="15" y1="15" x2="21" y2="21"/><line x1="4" y1="4" x2="9" y2="9"/></svg>
                                <span>Shuffle</span>
                            </button>
                        </div>
                    </div>
                </section>

                <!-- 2. Split Row: Spotlight & Up Next in Queue -->
                <section class="spotlight-queue-grid">
                    <!-- Left: Spotlight Card -->
                    <div class="spotlight-card">
                        <div class="spotlight-backdrop">
                            <img src="$spotlightThumb" class="spotlight-backdrop-img" alt="$spotlightTitle" onerror="this.src='https://images.unsplash.com/photo-1514933651103-005eec06c04b?auto=format&fit=crop&w=1200&q=80';" />
                            <div class="spotlight-backdrop-gradient"></div>
                            <div class="spotlight-top-tags">
                                <div class="spotlight-tag-left">
                                    <span class="tag-chip tag-chip-red">NOW PLAYING SPOTLIGHT</span>
                                    <span class="tag-chip">$spotlightBadge</span>
                                </div>
                                <span class="tag-chip" style="font-family:var(--font-mono);">LOSSLESS</span>
                            </div>
                        </div>

                        <div class="spotlight-body">
                            <div class="spotlight-hero-meta">$spotlightSub</div>
                            <div class="spotlight-title-row">
                                <h2 class="spotlight-title">$spotlightTitle</h2>
                                <div class="connected-tv-badge">
                                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="7" width="20" height="15" rx="2" ry="2"/><polyline points="17 2 12 7 7 2"/></svg>
                                    <span>DIRECT PLAY READY</span>
                                </div>
                            </div>
                            <p class="spotlight-synopsis">$spotlightDesc</p>
                            <div class="spotlight-spec-chips">
                                <span class="spec-pill">DIRECT STREAM</span>
                                <span class="spec-pill">NATIVE AUDIO</span>
                                <span class="spec-pill spec-pill-rating">★ READY</span>
                                <span class="spec-pill">NO RE-ENCODING</span>
                            </div>

                            <div class="spotlight-action-row" style="margin-top:20px;">
                                <div class="spotlight-btns-left">
                                    <button class="btn-resume" onclick="playMedia('$firstStreamUrl', '$firstTitle', $spotlightResumeSeconds)">
                                        <svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>
                                        <span>${if (spotlightResumeSeconds > 0) "Resume Watching" else "Stream Now"}</span>
                                    </button>
                                    <button class="btn-start-over" onclick="playMedia('$firstStreamUrl', '$firstTitle', 0)">${if (spotlightResumeSeconds > 0) "Start from Beginning" else "Direct Play"}</button>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- Right: Up Next in Queue -->
                    <div class="queue-panel">
                        <div class="section-header-row">
                            <div class="section-lead-title">
                                <div class="red-square-indicator"></div>
                                <span>UP NEXT IN QUEUE</span>
                            </div>
                            <div style="display:flex; gap:12px; align-items:center;">
                                <span style="font-family:var(--font-mono); font-size:11px; color:var(--text-muted); font-weight:700;">$queueCount TITLES</span>
                                <span class="section-sub-action" onclick="shufflePlay()">↻ REORDER</span>
                            </div>
                        </div>

                        <div class="queue-list">
                            $queueItemsHtml
                        </div>

                        <button class="add-queue-btn" onclick="document.getElementById('main-search-input').focus()">
                            <span>+ SEARCH &amp; ADD MEDIA</span>
                        </button>
                    </div>
                </section>

                <!-- 3. Watch Together Lounge -->
                <section class="sync-section-wrap">
                    <div class="sync-header-row">
                        <div class="sync-lead-col">
                            <div class="sync-protocol-tag">
                                <div class="red-square-indicator"></div>
                                <span>SYNC PROTOCOL V3</span>
                                <span>// LOCAL BROADCAST</span>
                            </div>
                            <h2 class="sync-h2">Watch Together Lounge</h2>
                            <p class="sync-sub">Stream synchronized with household devices and friends in real-time.</p>
                        </div>
                        <button class="btn-start-party" onclick="playMedia('$firstStreamUrl', '$firstTitle')">
                            <span>▶ BROADCAST STREAM</span>
                        </button>
                    </div>

                    <div class="sync-panels-grid">
                        <!-- Left: Room Info -->
                        <div class="room-info-card">
                            <div class="room-name-row">
                                <div class="room-name-title">
                                    <div class="green-dot"></div>
                                    <span>PocketNode Theater</span>
                                </div>
                                <span class="zero-delay-pill">Direct Stream — Lossless</span>
                            </div>

                            <div class="room-specs-table">
                                <div class="room-spec-col">
                                    <span class="spec-label">ENDPOINT</span>
                                    <span class="spec-val">Local HTML5</span>
                                </div>
                                <div class="room-spec-col">
                                    <span class="spec-label">ACTIVE MEDIA</span>
                                    <span class="spec-val" style="overflow:hidden; text-overflow:ellipsis; white-space:nowrap; max-width:120px;">$firstTitle</span>
                                </div>
                                <div class="room-spec-col">
                                    <span class="spec-label">STATUS</span>
                                    <span class="spec-val" style="color:var(--pn-red);">READY</span>
                                </div>
                            </div>

                            <div class="audio-lossless-row">
                                <span>Audio Pass-Through: Original Bitstream</span>
                                <span class="badge-flac">LOSSLESS</span>
                            </div>

                            <div class="connected-avatars-row">
                                <div class="avatar-stack">
                                    <div class="stacked-avatar av-k">A</div>
                                    <div class="stacked-avatar av-s">H</div>
                                </div>
                                <span class="members-label">Host Admin (Online)</span>
                            </div>

                            <button class="btn-join-sync" onclick="playMedia('$firstStreamUrl', '$firstTitle')">
                                <span>▶ START STREAMING</span>
                            </button>
                        </div>

                        <!-- Right: Live Notes & Reactions -->
                        <div class="chat-reactions-card">
                            <div class="chat-head-row">
                                <span class="chat-title">LOCAL ROOM NOTES &amp; REACTIONS</span>
                                <span style="font-family:var(--font-mono); font-size:11px; color:#22C55E; font-weight:800;">LIVE</span>
                            </div>

                            <div id="sync-chat-feed" class="chat-feed">
                                <div class="chat-msg">
                                    <div class="msg-meta-row">
                                        <span class="msg-user" style="color:var(--pn-red);">PocketNode Bot</span>
                                        <span class="msg-time">System</span>
                                    </div>
                                    <div class="msg-text">Welcome to PocketNode Watch Lounge. Drop notes or timestamps to share with household peers.</div>
                                </div>
                            </div>

                            <div class="chat-input-row">
                                <input id="chat-msg-input" class="chat-input-box" type="text" placeholder="Drop note or timestamp..." onkeydown="if(event.key==='Enter')sendChatMsg()" />
                                <button class="btn-chat-send" onclick="sendChatMsg()">SEND</button>
                            </div>
                        </div>
                    </div>
                </section>

                ${if (continueWatchingCardsHtml.isNotBlank()) """
                <!-- Continue Watching / Resume -->
                <section class="shelf-section">
                    <div class="section-header-row">
                        <div class="section-lead-title">
                            <div class="red-square-indicator"></div>
                            <span>CONTINUE WATCHING</span>
                            <span style="font-family:var(--font-mono); font-size:11px; color:var(--text-muted); font-weight:700;">RESUME PLAYBACK</span>
                        </div>
                        <span class="section-sub-action">IN PROGRESS</span>
                    </div>

                    <div class="shelf-grid">
                        $continueWatchingCardsHtml
                    </div>
                </section>
                """ else ""}

                <!-- 4. All Movies & Videos in Library -->
                <section class="shelf-section">
                    <div class="section-header-row">
                        <div class="section-lead-title">
                            <div class="red-square-indicator"></div>
                            <span>ALL LIBRARY MOVIES &amp; VIDEOS</span>
                            <span style="font-family:var(--font-mono); font-size:11px; color:var(--text-muted); font-weight:700;">$allVideoCount TITLES</span>
                        </div>
                        <span class="section-sub-action" onclick="document.getElementById('main-search-input').focus()">SEARCH LIBRARY →</span>
                    </div>

                    <div class="shelf-grid">
                        $recentlyAddedCards
                    </div>
                </section>

                <!-- 5. Offline Sandbox Vault -->
                <section class="shelf-section">
                    <div class="section-header-row">
                        <div class="section-lead-title">
                            <div class="red-square-indicator"></div>
                            <span>OFFLINE SANDBOX VAULT</span>
                            <span style="font-family:var(--font-mono); font-size:11px; color:var(--text-muted); font-weight:700;">LOCAL DEVICE STORAGE</span>
                        </div>
                        <span class="section-sub-action">OFFLINE READY</span>
                    </div>

                    <div class="shelf-grid">
                        $sandboxCardsHtml
                    </div>
                </section>

                <!-- 6. Popular & Trending Torrents -->
                <section class="shelf-section">
                    <div class="section-header-row">
                        <div class="section-lead-title">
                            <div class="red-square-indicator"></div>
                            <span>TRENDING TORRENTS (ONE-CLICK STREAM)</span>
                            <span style="font-family:var(--font-mono); font-size:11px; color:var(--text-muted); font-weight:700;">STREMIO PROTOCOL</span>
                        </div>
                        <span class="section-sub-action" onclick="document.getElementById('main-search-input').focus()">EXPLORE SWARM →</span>
                    </div>

                    <div class="shelf-grid">
                        $trendingCardsHtml
                    </div>
                </section>

                <!-- 7. Split: Telegram Cloud Library & Offline Storage -->
                <section class="split-shares-storage">
                    <!-- Left: Telegram Cloud Library -->
                    <div class="shares-card">
                        <div class="section-header-row">
                            <div class="section-lead-title">
                                <div class="red-square-indicator"></div>
                                <span>Telegram Cloud Library</span>
                                <span style="font-family:var(--font-mono); font-size:11px; color:var(--text-muted); font-weight:700;">SAVED &amp; FORWARDED</span>
                            </div>
                            <span class="section-sub-action" onclick="triggerScan(event)">SYNC BOT</span>
                        </div>

                        <div class="share-items-list">
                            $telegramCardsHtml
                        </div>
                    </div>

                    <!-- Right: Saved for Offline -->
                    <div class="storage-card">
                        <div class="section-header-row">
                            <div class="section-lead-title">
                                <div class="red-square-indicator"></div>
                                <span>Saved for Offline</span>
                                <span style="font-family:var(--font-mono); font-size:11px; color:var(--text-muted); font-weight:700;">DISK TELEMETRY</span>
                            </div>
                            <span class="section-sub-action">Disk Space</span>
                        </div>

                        <div class="storage-meter-box">
                            <div class="meter-info-row">
                                <span>Storage Used: $storageUsedStr of $storageTotalStr</span>
                                <span style="color:var(--pn-red); font-weight:700;">$storagePercent% Allocated</span>
                            </div>
                            <div class="meter-track">
                                <div class="meter-fill" style="width: $storagePercent%;"></div>
                            </div>
                        </div>

                        <div class="offline-items-list">
                            $offlineFilesHtml
                        </div>

                        <button class="btn-travel-mode" onclick="document.getElementById('main-search-input').focus()">
                            <span>🔍 SEARCH &amp; STREAM MEDIA</span>
                        </button>
                    </div>
                </section>

                <!-- Mobile Dark Status Pill -->
                <div class="mobile-system-status-pill">
                    <div class="mobile-status-top-row">
                        <div class="mobile-status-ok">● DIRECT PLAY OK</div>
                        <span style="color:#94A3B8;">HOST: POCKET-ALPHA-01</span>
                    </div>
                    <div class="mobile-status-mid">ZFS STORAGE POOL 38.4 TB / 48.0 TB (80%)</div>
                    <div class="mobile-status-bottom">TRANSCODER CODEC NVDEC / VAAPI NVENC</div>
                </div>

                <!-- 8. Telemetry & Footer Diagnostics -->
                <footer class="system-footer-wrap">
                    <div class="footer-meta-line">
                        <span>▲ Jellyfin Server Core v10.9.1 | Local Node Online</span>
                        <div class="footer-links">
                            <a href="#privacy">Privacy</a>
                            <a href="#diagnostics" onclick="triggerScan(event)">System Diagnostics</a>
                            <span>© 2026 Jellyfin Media</span>
                        </div>
                    </div>

                    <div class="telemetry-card">
                        <div class="telemetry-left">
                            <div class="logo-squircle" style="width:36px; height:36px; border-radius:10px;">
                                <svg width="24" height="24" viewBox="0 0 100 100" fill="none">
                                    <rect width="100" height="100" rx="26" fill="#E11D48"/>
                                    <polygon points="44,44 44,56 56,50" fill="#FFFFFF"/>
                                    <circle cx="67" cy="50" r="3.5" fill="#2DD4BF"/>
                                </svg>
                            </div>
                            <div>
                                <div class="telemetry-title">PN × JELLYFIN HUB <span style="font-family:var(--font-mono); font-size:11px; color:var(--pn-red);">CORE v10.9.1</span></div>
                                <div class="telemetry-desc">High performance editorial personal media streaming architecture. Local-first lossless transcoding node.</div>
                            </div>
                        </div>
                        <div class="telemetry-stats-row">
                            <div>NODE STATUS: <span class="stat-highlight">● DIRECT PLAY OK</span></div>
                            <div>STORAGE: <strong style="color:var(--text-heading);">38.4 TB / 48.0 TB</strong></div>
                            <span style="cursor:pointer;" onclick="triggerScan(event)">TELEMETRY</span>
                            <span>CODECS</span>
                            <span>LOGS</span>
                            <span>© 2026 JELLYFIN NODE</span>
                        </div>
                    </div>
                </footer>
            </main>

            <!-- Bottom Navigation Bar for Mobile -->
            <div class="mobile-bottom-nav">
                <button class="bottom-nav-item active" onclick="switchNavTab(this, 'movies')">
                    <span class="bottom-nav-icon">🎬</span>
                    <span>Movies</span>
                </button>
                <button class="bottom-nav-item" onclick="switchNavTab(this, 'shows')">
                    <span class="bottom-nav-icon">📺</span>
                    <span>Shows</span>
                </button>
                <button class="bottom-nav-item" onclick="switchNavTab(this, 'playlists')">
                    <span class="bottom-nav-icon">📑</span>
                    <span>Playlists</span>
                </button>
                <button class="bottom-nav-item" onclick="switchNavTab(this, 'party')">
                    <span class="bottom-nav-icon">👥</span>
                    <span>Party</span>
                </button>
                <button class="bottom-nav-item" onclick="openAppModal()">
                    <span class="bottom-nav-icon">📲</span>
                    <span>Get App</span>
                </button>
                <button class="bottom-nav-item" onclick="triggerScan(event)">
                    <span class="bottom-nav-icon">⚙️</span>
                    <span>System</span>
                </button>
            </div>

            <!-- Video Player Modal Overlay -->
            <div id="direct-player-container">
                <video id="video-player" playsinline webkit-playsinline controls autoplay preload="metadata"></video>
                <div id="player-osd-top" class="player-osd-header">
                    <button class="player-back-btn" onclick="closePlayer()" title="Back to Library (Esc / Return)">
                        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
                    </button>
                    <div style="flex: 1; min-width: 0;">
                        <div id="player-media-title" class="player-title-main"></div>
                        <div class="player-title-sub">PN-NODE × JELLYFIN • Direct Stream Lossless</div>
                    </div>
                    <div class="player-osd-actions">
                        <button class="player-ext-pill" onclick="openCurrentInApp('vlc')" title="Open stream in VLC for iOS / Android">
                            <span style="font-size: 12px;">🟧</span> <span>VLC</span>
                        </button>
                        <button class="player-ext-pill" onclick="openCurrentInApp('infuse')" title="Open stream in Infuse Player">
                            <span style="font-size: 12px;">🔥</span> <span>Infuse</span>
                        </button>
                        <button class="player-ext-pill" onclick="openCurrentInApp('outplayer')" title="Open stream in Outplayer">
                            <span style="font-size: 12px;">▶</span> <span>Outplayer</span>
                        </button>
                    </div>
                </div>

                <!-- Fallback / Unsupported Format Modal -->
                <div id="player-fallback-overlay" class="player-fallback-modal" style="display: none;">
                    <div class="player-fallback-card">
                        <div style="font-size: 2.2rem; margin-bottom: 0.5rem;">🎬</div>
                        <div class="fallback-badge">Playback Helper</div>
                        <h3 class="fallback-title" id="fallback-modal-title">Format Requires External Player</h3>
                        <p class="fallback-desc" id="fallback-modal-desc">
                            Safari on iOS cannot decode Matroska (.mkv) video files or multichannel DTS/AC3 audio tracks natively in browser tabs.
                            <br><br>
                            Tap below to stream directly with lossless quality and hardware acceleration in any installed player:
                        </p>
                        <div class="fallback-actions-grid">
                            <button class="fallback-btn fallback-btn-vlc" onclick="openCurrentInApp('vlc')">
                                <span class="fb-btn-icon">🟧</span>
                                <div class="fb-btn-text">
                                    <div class="fb-btn-primary">Open in VLC</div>
                                    <div class="fb-btn-sub">Instant playback • Lossless quality • Free</div>
                                </div>
                            </button>
                            <button class="fallback-btn fallback-btn-infuse" onclick="openCurrentInApp('infuse')">
                                <span class="fb-btn-icon">🔥</span>
                                <div class="fb-btn-text">
                                    <div class="fb-btn-primary">Open in Infuse</div>
                                    <div class="fb-btn-sub">High-performance 4K HDR playback for iOS</div>
                                </div>
                            </button>
                            <button class="fallback-btn fallback-btn-outplayer" onclick="openCurrentInApp('outplayer')">
                                <span class="fb-btn-icon">▶</span>
                                <div class="fb-btn-text">
                                    <div class="fb-btn-primary">Open in Outplayer</div>
                                    <div class="fb-btn-sub">Fast &amp; modern player for iOS</div>
                                </div>
                            </button>
                            <button class="fallback-btn" onclick="downloadCurrentStream()">
                                <span class="fb-btn-icon">⬇️</span>
                                <div class="fb-btn-text">
                                    <div class="fb-btn-primary">Direct Download File</div>
                                    <div class="fb-btn-sub">Save video file to device</div>
                                </div>
                            </button>
                            <button class="fallback-btn" onclick="copyCurrentStreamUrl()">
                                <span class="fb-btn-icon">📋</span>
                                <div class="fb-btn-text">
                                    <div class="fb-btn-primary" id="copy-btn-label">Copy Stream URL</div>
                                    <div class="fb-btn-sub">Paste into any media app (IINA, PotPlayer, MPV)</div>
                                </div>
                            </button>
                        </div>
                        <div class="fallback-footer">
                            <button class="fallback-btn-dismiss" onclick="dismissFallbackOverlay()">Dismiss / Try In-Browser Play</button>
                        </div>
                    </div>
                </div>
            </div>

            <!-- App Distribution Modal -->
            <div id="app-download-modal" class="player-fallback-modal" style="display: none;">
                <div class="player-fallback-card" style="max-width: 520px; text-align: left;">
                    <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px;">
                        <div style="display: flex; align-items: center; gap: 10px;">
                            <div style="font-size: 26px;">📲</div>
                            <div>
                                <h3 style="margin: 0; font-size: 18px; font-weight: 800; color: #FFFFFF;">PocketNode Apps</h3>
                                <div style="font-size: 11.5px; color: #94A3B8;">Direct APK Downloads &amp; TV Client Installation</div>
                            </div>
                        </div>
                        <button onclick="closeAppModal()" style="background: rgba(255,255,255,0.1); border: none; color: #FFFFFF; width: 32px; height: 32px; border-radius: 50%; cursor: pointer; display: flex; align-items: center; justify-content: center; font-size: 16px;">✕</button>
                    </div>

                    <div style="display: flex; flex-direction: column; gap: 14px;">
                        <!-- Client Card -->
                        <div style="background: #1F2937; border: 1px solid rgba(255,255,255,0.1); border-radius: 14px; padding: 14px 16px;">
                            <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px;">
                                <div>
                                    <span style="background: #0284c7; color: white; font-size: 10px; font-weight: 800; padding: 2px 7px; border-radius: 4px; text-transform: uppercase;">Android TV &amp; Mobile</span>
                                    <h4 style="margin: 6px 0 2px; font-size: 15px; color: #FFFFFF; font-weight: 700;">PocketNode Client (Player)</h4>
                                    <div style="font-size: 11.5px; color: #94A3B8;">ExoPlayer, Remote Control, Auto-Pairing • <span id="client-apk-size">21.4 MB</span></div>
                                </div>
                            </div>
                            <div style="display: flex; gap: 8px; margin-top: 10px;">
                                <a href="/apk/client" download="pocketnode-client.apk" class="btn-get-app" style="text-decoration: none; flex: 1; justify-content: center; padding: 10px 0;">
                                    ⬇️ DOWNLOAD CLIENT APK
                                </a>
                                <button onclick="copyApkLink('client')" class="fallback-btn" style="width: auto; padding: 10px 14px; margin: 0;" title="Copy direct download link">
                                    📋
                                </button>
                            </div>
                        </div>

                        <!-- Server Card -->
                        <div style="background: #1F2937; border: 1px solid rgba(255,255,255,0.1); border-radius: 14px; padding: 14px 16px;">
                            <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px;">
                                <div>
                                    <span style="background: var(--pn-red); color: white; font-size: 10px; font-weight: 800; padding: 2px 7px; border-radius: 4px; text-transform: uppercase;">Home Server</span>
                                    <h4 style="margin: 6px 0 2px; font-size: 15px; color: #FFFFFF; font-weight: 700;">PocketNode Server (Core)</h4>
                                    <div style="font-size: 11.5px; color: #94A3B8;">Jellyfin Server, UPnP/DLNA, Torrents, Telegram • <span id="server-apk-size">62.9 MB</span></div>
                                </div>
                            </div>
                            <div style="display: flex; gap: 8px; margin-top: 10px;">
                                <a href="/apk/server" download="pocketnode-server.apk" class="btn-get-app" style="background: #374151; border: 1px solid rgba(255,255,255,0.2); text-decoration: none; flex: 1; justify-content: center; padding: 10px 0;">
                                    ⬇️ DOWNLOAD SERVER APK
                                </a>
                                <button onclick="copyApkLink('server')" class="fallback-btn" style="width: auto; padding: 10px 14px; margin: 0;" title="Copy direct download link">
                                    📋
                                </button>
                            </div>
                        </div>

                        <!-- TV Install Helper -->
                        <div style="background: rgba(15, 23, 42, 0.6); border: 1px solid rgba(255,255,255,0.06); border-radius: 12px; padding: 12px; font-size: 11.5px; color: #94A3B8; line-height: 1.5;">
                            <strong style="color: #F8FAFC;">💡 Installing on Android TV / Fire TV:</strong><br>
                            1. Open the <strong>Downloader by AFTVnews</strong> app on your TV.<br>
                            2. Enter your server's client URL: <code style="color: #FB7185;" id="tv-downloader-url">http://.../apk/client</code><br>
                            3. Click Go to download and install with 1-click!
                        </div>
                    </div>
                </div>
            </div>

            <!-- Interaction Scripts -->
            <script>
                var osdHideTimer = null;

                function cleanMediaTitle(raw) {
                    if (!raw) return '';
                    var s = raw;
                    s = s.replace(/\.(mp4|mkv|avi|mov|webm|flv|wmv|ts)\b/gi, '');
                    s = s.replace(/[\._]/g, ' ');
                    s = s.replace(/\b(1080p|2160p|4k|720p|480p|uhd|hdr|hdr10|10bit|webrip|web-dl|bluray|brrip|h264|h265|x264|x265|hevc|aac|dts|ddp5\.1|6ch|ac3|repack)\b.*/gi, '');
                    return s.trim() || raw;
                }

                var activePlayTitle = '';
                var activePlayUrl = '';
                var lastSavedProgressSec = 0;

                function openCurrentInApp(app) {
                    if (!activePlayUrl) return;
                    var fullUrl = new URL(activePlayUrl, window.location.origin).href;
                    if (app === 'vlc') {
                        window.location.href = 'vlc://' + fullUrl;
                        setTimeout(function() {
                            window.location.href = 'vlc-x-callback://x-callback-url/stream?url=' + encodeURIComponent(fullUrl);
                        }, 400);
                    } else if (app === 'infuse') {
                        window.location.href = 'infuse://' + fullUrl;
                    } else if (app === 'outplayer') {
                        window.location.href = 'outplayer://' + fullUrl;
                    }
                }

                function downloadCurrentStream() {
                    if (!activePlayUrl) return;
                    var fullUrl = new URL(activePlayUrl, window.location.origin).href;
                    var a = document.createElement('a');
                    a.href = fullUrl;
                    a.download = cleanMediaTitle(activePlayTitle) || 'video';
                    document.body.appendChild(a);
                    a.click();
                    document.body.removeChild(a);
                }

                function copyCurrentStreamUrl() {
                    if (!activePlayUrl) return;
                    var fullUrl = new URL(activePlayUrl, window.location.origin).href;
                    if (navigator.clipboard && navigator.clipboard.writeText) {
                        navigator.clipboard.writeText(fullUrl).then(function() {
                            var label = document.getElementById('copy-btn-label');
                            if (label) {
                                var old = label.innerText;
                                label.innerText = 'Copied to Clipboard! ✓';
                                setTimeout(function() { label.innerText = old; }, 2000);
                            }
                        });
                    } else {
                        prompt('Copy stream URL:', fullUrl);
                    }
                }

                function showFallbackOverlay(reason) {
                    var overlay = document.getElementById('player-fallback-overlay');
                    if (!overlay) return;
                    overlay.style.display = 'flex';
                }

                function dismissFallbackOverlay() {
                    var overlay = document.getElementById('player-fallback-overlay');
                    if (overlay) overlay.style.display = 'none';
                }

                function playMedia(url, title, resumeSec) {
                    if (!url) {
                        alert('No stream URL available.');
                        return;
                    }
                    dismissFallbackOverlay();
                    activePlayTitle = title || 'Media';
                    activePlayUrl = url;
                    lastSavedProgressSec = 0;

                    var container = document.getElementById('direct-player-container');
                    var video = document.getElementById('video-player');
                    var titleEl = document.getElementById('player-media-title');

                    if (titleEl) titleEl.innerText = cleanMediaTitle(title);

                    document.documentElement.style.overflow = 'hidden';
                    document.body.style.overflow = 'hidden';

                    container.style.display = 'block';
                    showPlayerOsd();

                    video.src = url;
                    video.load();

                    var targetSec = resumeSec || 0;
                    if (!targetSec) {
                        try {
                            var saved = localStorage.getItem('pn_resume_' + encodeURIComponent(cleanMediaTitle(title)));
                            if (saved) {
                                var parsed = JSON.parse(saved);
                                if (parsed.pos && parsed.pos > 4) targetSec = parsed.pos;
                            }
                        } catch (_) {}
                    }

                    var hasResumed = false;
                    function onCanPlay() {
                        if (!hasResumed && targetSec > 3) {
                            hasResumed = true;
                            try { video.currentTime = targetSec; } catch (_) {}
                        }
                    }
                    video.addEventListener('loadedmetadata', onCanPlay, { once: true });
                    video.addEventListener('canplay', onCanPlay, { once: true });

                    var p = video.play();
                    if (p !== undefined) {
                        p.catch(function(e) {
                            console.log('Video autoplay gesture deferred:', e);
                        });
                    }

                    // iOS Safari Matroska/DTS codec detector
                    var isIOS = /iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
                    var isLikelyMkv = (url && url.toLowerCase().indexOf('.mkv') !== -1) ||
                                      (title && title.toLowerCase().indexOf('.mkv') !== -1);
                    if (isIOS && isLikelyMkv) {
                        setTimeout(function() {
                            if (video.paused && video.currentTime === 0 && (!video.readyState || video.readyState < 2)) {
                                showFallbackOverlay('Safari iOS requires external player for MKV/AC3');
                            }
                        }, 1400);
                    }
                }

                function closePlayer() {
                    dismissFallbackOverlay();
                    if (osdHideTimer) clearTimeout(osdHideTimer);
                    var container = document.getElementById('direct-player-container');
                    var video = document.getElementById('video-player');
                    try {
                        var cur = Math.floor(video.currentTime || 0);
                        var dur = Math.floor(video.duration || 0);
                        if (cur > 3 && dur > 5) {
                            fetch('/api/playback/progress', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify({
                                    id: '',
                                    title: cleanMediaTitle(activePlayTitle),
                                    streamUrl: activePlayUrl,
                                    positionSeconds: cur,
                                    durationSeconds: dur
                                })
                            }).catch(function() {});
                        }
                        video.pause();
                    } catch (_) {}
                    video.removeAttribute('src');
                    video.load();
                    container.style.display = 'none';

                    document.documentElement.style.overflow = '';
                    document.body.style.overflow = '';
                }

                function showPlayerOsd() {
                    var osd = document.getElementById('player-osd-top');
                    if (osd) osd.classList.remove('hidden');
                    scheduleOsdHide();
                }

                function scheduleOsdHide() {
                    if (osdHideTimer) clearTimeout(osdHideTimer);
                    var video = document.getElementById('video-player');
                    if (video && !video.paused) {
                        osdHideTimer = setTimeout(function() {
                            var osd = document.getElementById('player-osd-top');
                            if (osd) osd.classList.add('hidden');
                        }, 3200);
                    }
                }

                var v = document.getElementById('video-player');
                if (v) {
                    v.addEventListener('webkitendfullscreen', closePlayer);
                    v.addEventListener('play', scheduleOsdHide);
                    v.addEventListener('pause', function() {
                        var osd = document.getElementById('player-osd-top');
                        if (osd) osd.classList.remove('hidden');
                        if (osdHideTimer) clearTimeout(osdHideTimer);
                    });
                    v.addEventListener('error', function(e) {
                        console.warn('Video error occurred in player:', v.error);
                        showFallbackOverlay('Browser cannot decode this video format directly');
                    });
                    v.addEventListener('timeupdate', function() {
                        var cur = Math.floor(v.currentTime || 0);
                        var dur = Math.floor(v.duration || 0);
                        if (!cur || !dur || dur < 5) return;

                        if (Math.abs(cur - lastSavedProgressSec) >= 3) {
                            lastSavedProgressSec = cur;
                            var clean = cleanMediaTitle(activePlayTitle);
                            try {
                                localStorage.setItem('pn_resume_' + encodeURIComponent(clean), JSON.stringify({
                                    title: clean,
                                    url: activePlayUrl,
                                    pos: cur,
                                    dur: dur,
                                    pct: cur / dur,
                                    ts: Date.now()
                                }));
                            } catch (_) {}

                            try {
                                fetch('/api/playback/progress', {
                                    method: 'POST',
                                    headers: { 'Content-Type': 'application/json' },
                                    body: JSON.stringify({
                                        id: '',
                                        title: clean,
                                        streamUrl: activePlayUrl,
                                        positionSeconds: cur,
                                        durationSeconds: dur
                                    })
                                }).catch(function() {});
                            } catch (_) {}
                        }
                    });
                    v.addEventListener('ended', function() {
                        try {
                            var clean = cleanMediaTitle(activePlayTitle);
                            localStorage.removeItem('pn_resume_' + encodeURIComponent(clean));
                            fetch('/api/playback/progress', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify({
                                    id: '',
                                    title: clean,
                                    streamUrl: activePlayUrl,
                                    positionSeconds: Math.floor(v.duration || 1000),
                                    durationSeconds: Math.floor(v.duration || 1000)
                                })
                            }).catch(function() {});
                        } catch (_) {}
                    });
                }

                var pContainer = document.getElementById('direct-player-container');
                if (pContainer) {
                    pContainer.addEventListener('mousemove', showPlayerOsd);
                    pContainer.addEventListener('touchstart', showPlayerOsd, { passive: true });
                }

                function filterMedia(query) {
                    var q = (query || '').toLowerCase().trim();
                    var cards = document.querySelectorAll('.movie-card, .trending-card, .queue-row');
                    cards.forEach(function(c) {
                        var txt = c.innerText.toLowerCase();
                        c.style.display = (!q || txt.includes(q)) ? '' : 'none';
                    });
                }

                function switchNavTab(btn, tab) {
                    document.querySelectorAll('.nav-tab-btn, .bottom-nav-item').forEach(function(el) {
                        el.classList.remove('active');
                    });
                    if (btn) btn.classList.add('active');
                }

                function shufflePlay() {
                    var cards = document.querySelectorAll('.movie-card');
                    if (cards.length > 0) {
                        var randIdx = Math.floor(Math.random() * cards.length);
                        cards[randIdx].click();
                    }
                }

                function sendChatMsg() {
                    var input = document.getElementById('chat-msg-input');
                    var feed = document.getElementById('sync-chat-feed');
                    if (!input || !feed) return;
                    var text = input.value.trim();
                    if (!text) return;

                    var now = new Date();
                    var timeStr = String(now.getHours()).padStart(2, '0') + ':' + String(now.getMinutes()).padStart(2, '0') + ':' + String(now.getSeconds()).padStart(2, '0');

                    var msgDiv = document.createElement('div');
                    msgDiv.className = 'chat-msg';
                    msgDiv.innerHTML = '<div class="msg-meta-row"><span class="msg-user" style="color:var(--pn-red);">You (Host)</span><span class="msg-time">' + timeStr + '</span></div><div class="msg-text">' + text.replace(/</g, '&lt;').replace(/>/g, '&gt;') + '</div>';
                    feed.appendChild(msgDiv);
                    feed.scrollTop = feed.scrollHeight;
                    input.value = '';
                }

                function triggerScan(event) {
                    if (event) event.stopPropagation();
                    fetch('/api/scan')
                        .then(function(r) { return r.json(); })
                        .then(function(data) {
                            alert('Media scan complete! Scanned ' + (data.scannedCount || 'items') + ' files.');
                            window.location.reload();
                        })
                        .catch(function() {
                            window.location.reload();
                        });
                }

                function openAppModal() {
                    var modal = document.getElementById('app-download-modal');
                    if (modal) {
                        modal.style.display = 'flex';
                        document.documentElement.style.overflow = 'hidden';
                        document.body.style.overflow = 'hidden';

                        var origin = window.location.origin;
                        var tvUrl = document.getElementById('tv-downloader-url');
                        if (tvUrl) tvUrl.textContent = origin + '/apk/client';

                        fetch('/api/apk/info')
                            .then(function(r) { return r.json(); })
                            .then(function(data) {
                                if (data && data.client && data.client.sizeFormatted) {
                                    var cel = document.getElementById('client-apk-size');
                                    if (cel) cel.textContent = data.client.sizeFormatted;
                                }
                                if (data && data.server && data.server.sizeFormatted) {
                                    var sel = document.getElementById('server-apk-size');
                                    if (sel) sel.textContent = data.server.sizeFormatted;
                                }
                            })
                            .catch(function() {});
                    }
                }

                function closeAppModal() {
                    var modal = document.getElementById('app-download-modal');
                    if (modal) {
                        modal.style.display = 'none';
                        document.documentElement.style.overflow = '';
                        document.body.style.overflow = '';
                    }
                }

                function copyApkLink(type) {
                    var url = window.location.origin + '/apk/' + (type || 'client');
                    if (navigator.clipboard && navigator.clipboard.writeText) {
                        navigator.clipboard.writeText(url).then(function() {
                            alert('Direct APK download link copied:\n' + url);
                        }).catch(function() {
                            prompt('Copy APK link:', url);
                        });
                    } else {
                        prompt('Copy APK link:', url);
                    }
                }

                var modalBg = document.getElementById('app-download-modal');
                if (modalBg) {
                    modalBg.addEventListener('click', function(e) {
                        if (e.target === modalBg) closeAppModal();
                    });
                }

                // Keyboard shortcuts (CTRL+K for search, Escape for player & modals)
                document.addEventListener('keydown', function(e) {
                    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
                        e.preventDefault();
                        var s = document.getElementById('main-search-input');
                        if (s) { s.focus(); s.select(); }
                    }
                    var key = e.keyCode || e.which;
                    var appModal = document.getElementById('app-download-modal');
                    if (appModal && appModal.style.display !== 'none' && appModal.style.display !== '') {
                        if (key === 27) {
                            e.preventDefault();
                            closeAppModal();
                            return;
                        }
                    }

                    var player = document.getElementById('direct-player-container');
                    var isPlayerOpen = player && player.style.display !== 'none' && player.style.display !== '';

                    if (key === 461 || key === 27) { // LG WebOS back or ESC
                        if (isPlayerOpen) {
                            e.preventDefault();
                            closePlayer();
                        }
                    }
                });
            </script>
        </body>
        </html>
        """.trimIndent()
    }
}
