export default function PlantPhoto({ src, alt, className = "" }) {
  return (
    <span className={`plant-photo ${className}`}>
      {src ? (
        <img src={src} alt={alt} />
      ) : (
        <svg viewBox="0 0 48 48" fill="none" aria-hidden="true">
          <path d="M24 41V23" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" />
          <path d="M24 29C14 29 11 22 11 15c8 0 13 3 13 14Z" fill="currentColor" opacity=".82" />
          <path d="M24 23c0-9 5-14 14-14 0 9-4 14-14 14Z" fill="currentColor" opacity=".58" />
          <path d="M18 41h12" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" />
        </svg>
      )}
    </span>
  );
}
