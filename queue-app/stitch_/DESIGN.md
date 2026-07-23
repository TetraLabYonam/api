---
name: Stable Care Mobility
colors:
  surface: '#f9f9f9'
  surface-dim: '#dadada'
  surface-bright: '#f9f9f9'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f3f3f3'
  surface-container: '#eeeeee'
  surface-container-high: '#e8e8e8'
  surface-container-highest: '#e2e2e2'
  on-surface: '#1a1c1c'
  on-surface-variant: '#43474f'
  inverse-surface: '#2f3131'
  inverse-on-surface: '#f1f1f1'
  outline: '#747780'
  outline-variant: '#c4c6d0'
  surface-tint: '#405f91'
  primary: '#001736'
  on-primary: '#ffffff'
  primary-container: '#002b5b'
  on-primary-container: '#7594ca'
  inverse-primary: '#a9c7ff'
  secondary: '#2a6767'
  on-secondary: '#ffffff'
  secondary-container: '#aeebea'
  on-secondary-container: '#2f6c6b'
  tertiary: '#390002'
  on-tertiary: '#ffffff'
  tertiary-container: '#600007'
  on-tertiary-container: '#ff5a53'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#d6e3ff'
  primary-fixed-dim: '#a9c7ff'
  on-primary-fixed: '#001b3d'
  on-primary-fixed-variant: '#264778'
  secondary-fixed: '#b1eeed'
  secondary-fixed-dim: '#95d1d0'
  on-secondary-fixed: '#002020'
  on-secondary-fixed-variant: '#064f4f'
  tertiary-fixed: '#ffdad6'
  tertiary-fixed-dim: '#ffb3ac'
  on-tertiary-fixed: '#410003'
  on-tertiary-fixed-variant: '#930010'
  background: '#f9f9f9'
  on-background: '#1a1c1c'
  surface-variant: '#e2e2e2'
typography:
  hero-num:
    fontFamily: Noto Sans KR
    fontSize: 72px
    fontWeight: '700'
    lineHeight: 84px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Noto Sans KR
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 44px
  headline-md:
    fontFamily: Noto Sans KR
    fontSize: 26px
    fontWeight: '700'
    lineHeight: 36px
  body-lg:
    fontFamily: Noto Sans KR
    fontSize: 20px
    fontWeight: '400'
    lineHeight: 30px
  body-md:
    fontFamily: Noto Sans KR
    fontSize: 18px
    fontWeight: '400'
    lineHeight: 28px
  label-lg:
    fontFamily: Noto Sans KR
    fontSize: 18px
    fontWeight: '700'
    lineHeight: 24px
    letterSpacing: 0.01em
  label-md:
    fontFamily: Noto Sans KR
    fontSize: 16px
    fontWeight: '500'
    lineHeight: 20px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  touch-target-min: 48px
  button-height-lg: 64px
  button-height-md: 56px
  container-padding: 20px
  stack-gap: 16px
  section-gap: 32px
---

## Brand & Style

This design system is built for accessibility, reliability, and warmth, specifically tailored for seniors (ages 60-80). The visual language prioritizes cognitive ease and physical comfort, moving away from "trendy" tech aesthetics toward a dependable, institutional feel that inspires trust.

The design style is **Corporate / Modern** with a focus on high-utility **Minimalism**. Every interface element is purposeful, avoiding decorative clutter or complex layered effects. The emotional response should be one of "stable guidance"—the UI acts as a calm, helpful assistant that never rushes the user. 

Key principles:
- **Explicitness:** No hidden gestures (no long-press, no complex swipes). 
- **Predictability:** Consistent placement of navigation and "Go Back" actions.
- **Foraging Safety:** High-visibility "Undo" and "Cancel" paths to reduce technology-induced anxiety.

## Colors

The palette is anchored in high-contrast stability. The background uses a **Warm White (#F9F9F9)** to reduce eye strain compared to pure white, while maintaining a clean, paper-like feel. 

- **Primary Deep Navy (#002B5B):** Used for primary actions, headers, and core brand elements to convey authority and stability.
- **Secondary Dark Teal (#004C4C):** Used for success states, secondary categories, or informational highlights.
- **Accent Red (#D32F2F):** Reserved strictly for warnings, errors, and "Stop" actions.
- **Contrast Compliance:** All text-on-background combinations must exceed WCAG AA standards (4.5:1 for body, 3:1 for large text). Primary Navy on Warm White provides a superior 13:1 ratio for maximum legibility.

## Typography

Legibility is the cornerstone of this design system. We use **Noto Sans KR** for its exceptional clarity in both Korean and alphanumeric characters. 

- **Size Floors:** No text should ever fall below 16px. The standard reading size is 18px-20px.
- **Hero Numbers:** For queue numbers or critical data points, use the 72px Hero style to ensure visibility from a distance.
- **Hierarchy:** Use bold weights (700) liberally for headers to create a clear "at-a-glance" structure. 
- **Line Height:** Increased leading (1.5x - 1.6x) is applied to body text to prevent lines from blurring together for users with visual impairments like presbyopia.

## Layout & Spacing

The layout follows a **Fixed Grid** approach for mobile, utilizing a single-column stack in most views to prevent cognitive overload.

- **Generous Margins:** A standard 20px side margin ensures that fingers holding the device do not obscure content.
- **Touch Targets:** Every interactive element must be at least 48x48px. Primary buttons are oversized (56-64px height) to accommodate lower motor precision.
- **Vertical Rhythm:** Elements are separated by clear 16px (related) or 32px (unrelated) gaps. 
- **Mobile First:** On larger screens (tablets), the content does not stretch excessively; instead, it maintains a comfortable reading measure (max 600px) centered on the screen.

## Elevation & Depth

This design system uses **Tonal Layers** and **Bold Borders** rather than complex shadows, which can sometimes appear blurry or confusing to seniors.

- **Flat Stacked Surfaces:** Use subtle background shifts (e.g., a slightly darker gray surface on the Warm White background) to indicate containers.
- **High-Contrast Outlines:** Interactive inputs and cards use a 1px or 2px solid border (#E0E0E0) to clearly define their boundaries.
- **Focus States:** When an input is selected, the border should thicken to 3px and change to Primary Navy. Do not rely on color alone for focus; always use a thickness change.
- **No Transparency:** Avoid glassmorphism or background blurs, as they reduce text contrast and can be visually disorienting.

## Shapes

The shape language is **Rounded**, using an 8px (0.5rem) base radius.

- **Reasoning:** Purely sharp corners (0px) can feel too harsh or "broken," while pill shapes can sometimes be mistaken for unclickable tags. The 8px radius provides a friendly, modern feel while maintaining enough structural "squareness" to look like a sturdy button or card.
- **Large Components:** Cards and large dialogs may use `rounded-lg` (16px) to further soften the interface.

## Components

### Buttons
- **Primary:** Navy background, White text, 64px height. Text must be Bold 20px.
- **Secondary:** White background, Navy border (2px), Navy text.
- **Destructive:** Red background, White text. Reserved for "Cancel" or "Delete."

### Cards (Job Listings/Status)
- Use a 1px border and a white background. 
- Headlines must be 26px Bold. 
- Information should be presented in a simple "Label: Value" list format.

### Status Badges
- **Receiving:** Green background (#2E7D32), White text.
- **Closed:** Medium Gray background (#757575), White text.
- **No Session:** Navy background, White text.
- Always include an icon (e.g., a checkmark or clock) alongside the text label.

### Input Fields
- Minimum 56px height.
- Label must always be visible above the field (no floating labels that disappear).
- Use a 2px border for the default state to ensure the target is obvious.

### Icons
- Use thick-stroke, "filled" icons for better visibility.
- **Requirement:** Every icon must be accompanied by a Korean text label. Icons never stand alone.

### Loading & Feedback
- **Loading:** Use a clear, slow-moving progress bar with the text "잠시만 기다려 주세요" (Please wait a moment).
- **Error Banners:** Solid Red top-bar with a "Close" (X) and an "Undo" button where applicable.