import type {InputHTMLAttributes, ReactNode} from 'react';
import {Icon, type IconName} from './Icon.tsx';

export function IconButton({icon, label, onClick}: {icon: IconName; label: string; onClick: () => void}) {
  return <button className="icon-btn" onClick={onClick} aria-label={label}><Icon name={icon} /></button>;
}

export function Empty({title, children}: {title: ReactNode; children?: ReactNode}) {
  return (
    <div className="empty">
      <div className="empty-illustration"><Icon name="leaf" /></div>
      <h3>{title}</h3>
      <p>{children}</p>
    </div>
  );
}

export function Hint({tone = '', icon, className = '', children}: {tone?: '' | 'orange' | 'gray'; icon?: IconName; className?: string; children?: ReactNode}) {
  return <div className={['hint', tone, className].filter(Boolean).join(' ')}>{icon && <Icon name={icon} />}<span>{children}</span></div>;
}

export function Field({label, type = 'text', ...input}: {label: ReactNode} & InputHTMLAttributes<HTMLInputElement>) {
  return <label className="field"><span>{label}</span><input type={type} {...input} /></label>;
}
