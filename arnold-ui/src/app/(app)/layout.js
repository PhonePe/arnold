import { AppLayout } from '@/components/layout/AppLayout';
import { Splash } from '@/components/layout/Splash';

const splashBootstrap = `(function(){
  try {
    var key = 'arnold.splash.lastShownAt';
    var repeatAfter = 7 * 24 * 60 * 60 * 1000;
    var now = Date.now();
    var lastShownAt = Number(localStorage.getItem(key));
    var shouldShow = !Number.isFinite(lastShownAt)
      || lastShownAt <= 0
      || lastShownAt > now
      || now - lastShownAt >= repeatAfter;

    if (shouldShow) {
      localStorage.setItem(key, String(now));
      document.documentElement.setAttribute('data-splash', 'show');
    } else {
      document.documentElement.setAttribute('data-splash', 'hide');
    }
  } catch (e) {
    document.documentElement.setAttribute('data-splash', 'show');
  }
})();`;

export default function DashboardLayout({ children }) {
  return (
    <>
      <script dangerouslySetInnerHTML={{ __html: splashBootstrap }} />
      <Splash />
      <AppLayout>{children}</AppLayout>
    </>
  );
}
