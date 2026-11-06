import { ImageResponse } from 'next/og';

export const alt = 'Launch: the productive Android launcher';
export const size = { width: 1200, height: 630 };
export const contentType = 'image/png';

export default function OpengraphImage() {
  return new ImageResponse(
    (
      <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', justifyContent: 'center', padding: 80, background: 'linear-gradient(135deg,#1b1f27 0%,#20242d 55%,#2e3440 100%)', color: '#eceff4' }}>
        <div style={{ fontSize: 34, color: '#88c0d0', fontWeight: 700 }}>Launch</div>
        <div style={{ fontSize: 76, fontWeight: 800, lineHeight: 1.05, marginTop: 20 }}>The launcher that does everything, quietly.</div>
        <div style={{ fontSize: 30, marginTop: 28, color: '#d8dee9' }}>Files · Camera · Documents · AI · Focus · Lock screen</div>
        <div style={{ fontSize: 26, marginTop: 40, color: '#81a1c1' }}>Open source · No ads · No tracking</div>
      </div>
    ),
    size,
  );
}
