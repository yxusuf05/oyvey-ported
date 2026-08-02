/** Minimal DOM helpers. The UI is plain elements and CSS — no framework, no virtual DOM. */

type Child = Node | string | null | undefined | false;

export function el<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  attrs: Record<string, string | number | boolean | EventListener | undefined> = {},
  ...children: Child[]
): HTMLElementTagNameMap[K] {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs)) {
    if (value === undefined || value === false) continue;
    if (key.startsWith('on') && typeof value === 'function') {
      node.addEventListener(key.slice(2).toLowerCase(), value as EventListener);
    } else if (key === 'class') {
      node.className = String(value);
    } else if (key === 'text') {
      node.textContent = String(value);
    } else if (key === 'value' && node instanceof HTMLInputElement) {
      node.value = String(value);
    } else {
      node.setAttribute(key, String(value));
    }
  }
  for (const child of children) {
    if (child === null || child === undefined || child === false) continue;
    node.append(typeof child === 'string' ? document.createTextNode(child) : child);
  }
  return node;
}

export function clear(node: HTMLElement): void {
  while (node.firstChild) node.firstChild.remove();
}

/** A labelled row with a control on the right and an optional live value readout. */
export function field(label: string, control: HTMLElement, valueText?: string): HTMLElement {
  const value = valueText === undefined ? null : el('span', { class: 'field__value', text: valueText });
  return el(
    'div',
    { class: 'field' },
    el('span', { class: 'field__label', text: label }),
    el('div', { class: 'row', style: 'margin:0' }, control, value),
  );
}

export function slider(
  value: number,
  min: number,
  max: number,
  step: number,
  onInput: (value: number) => void,
): { input: HTMLInputElement; readout: HTMLSpanElement } {
  const readout = el('span', { class: 'field__value' });
  const input = el('input', {
    type: 'range',
    min: String(min),
    max: String(max),
    step: String(step),
    value: String(value),
  }) as HTMLInputElement;
  input.addEventListener('input', () => onInput(Number(input.value)));
  return { input, readout };
}

export function toggle(checked: boolean, onChange: (checked: boolean) => void): HTMLButtonElement {
  const button = el('button', { class: 'tab', text: checked ? 'ON' : 'OFF' });
  button.setAttribute('aria-selected', String(checked));
  button.addEventListener('click', () => {
    const next = button.getAttribute('aria-selected') !== 'true';
    button.setAttribute('aria-selected', String(next));
    button.textContent = next ? 'ON' : 'OFF';
    onChange(next);
  });
  return button;
}
