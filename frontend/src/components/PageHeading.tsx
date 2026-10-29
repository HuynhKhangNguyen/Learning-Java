import { useEffect } from 'react';

type Props = { eyebrow: string; title: string; description?: string };

export function PageHeading({ eyebrow, title, description }: Props) {
  useEffect(() => { document.title = `${title} · Backend Lab`; }, [title]);

  return (
    <header className="page-heading">
      <p className="eyebrow">{eyebrow}</p>
      <h1>{title}</h1>
      {description && <p className="lead">{description}</p>}
    </header>
  );
}
