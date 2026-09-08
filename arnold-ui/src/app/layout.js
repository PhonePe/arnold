import localFont from 'next/font/local';
import './globals.css';

const spaceGrotesk = localFont({
  src: './fonts/space-grotesk/SpaceGrotesk.ttf',
  variable: '--font-space-grotesk',
  weight: '400 700',
  style: 'normal',
  display: 'swap',
});

const ibmPlexMono = localFont({
  src: [
    { path: './fonts/ibm-plex-mono/IBMPlexMono-Regular.ttf', weight: '400', style: 'normal' },
    { path: './fonts/ibm-plex-mono/IBMPlexMono-Medium.ttf', weight: '500', style: 'normal' },
    { path: './fonts/ibm-plex-mono/IBMPlexMono-SemiBold.ttf', weight: '600', style: 'normal' },
  ],
  variable: '--font-ibm-plex-mono',
  display: 'swap',
});

export const metadata = {
  title: 'Arnold · Lattice — AI code review',
  description: 'AI-driven code review platform for GitLab',
  icons: {
    icon: [{ url: '/favicon.svg', type: 'image/svg+xml' }],
  },
};

const themeBootstrap = `(function(){try{var t=localStorage.getItem('arnold.theme');if(!t){t=matchMedia('(prefers-color-scheme: dark)').matches?'dark':'light';}document.documentElement.setAttribute('data-theme',t);}catch(e){document.documentElement.setAttribute('data-theme','light');}})();`;

export default function RootLayout({ children }) {
  return (
    <html
      lang="en"
      className={`${spaceGrotesk.variable} ${ibmPlexMono.variable} h-full antialiased`}
      suppressHydrationWarning
    >
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeBootstrap }} />
      </head>
      <body className="min-h-full flex flex-col">{children}</body>
    </html>
  );
}
