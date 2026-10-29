import type { ReactNode } from 'react';
import { Construction } from 'lucide-react';

type Props = { title: string; children: ReactNode; actions?: ReactNode };

export function PreparationPanel({ title, children, actions }: Props) {
  return (
    <section className="panel preparation-panel">
      <div className="section-symbol" aria-hidden="true"><Construction /></div>
      <div className="stack"><h2>{title}</h2><div className="muted readable">{children}</div>{actions && <div className="actions">{actions}</div>}</div>
    </section>
  );
}
