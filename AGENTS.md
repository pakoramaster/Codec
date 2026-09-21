# Repository Guidelines

## UI Direction

The user interface is a core part of this project and must be treated as a
first-class product requirement, not as incidental styling.

- Keep the UI strongly inspired by the original *Metal Gear Solid* (1998)
  interface, especially its Codec presentation, visual hierarchy, atmosphere,
  typography, colors, framing, and interaction feel.
- Use the *Metal Gear Solid* UI collection on Game UI Database as the primary
  visual reference when creating or reviewing UI changes:
  https://www.gameuidatabase.com/gameData.php?id=2244
- Preserve the project's established visual identity. New screens and
  components should feel cohesive with the existing Codec-inspired interface.
- Prefer deliberate, game-authentic UI choices over generic Android or Material
  defaults when doing so remains usable and accessible.
- Check UI changes at representative screen sizes and avoid regressions in
  layout, readability, animation, audio-visual presentation, and interaction.

## Scope and Refactoring

- Do not completely refactor, rewrite, or substantially restructure the
  codebase without asking the user for explicit approval first.
- Prefer focused, incremental changes that preserve existing behavior and
  architecture unless the requested task requires otherwise.
- If a broad refactor appears necessary, explain why, describe the proposed
  scope and risks, and wait for the user's approval before proceeding.
- Do not bundle unrelated cleanup or architectural changes into a focused task.

