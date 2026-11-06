import type { DocPage } from '../types';

export const faqPages: Record<string, DocPage> = {
  faq: {
    title: 'FAQ & Troubleshooting',
    sections: [
      {
        heading: 'Setup',
        content: `**How do I make Launch my default home app?**
Use the last page of the app tour, or open Android settings and choose Launch under Default home app.

**Can I use Launch without most permissions?**
Yes. Each permission unlocks one feature, and the launcher works without any optional one.

**Do I need an account?**
No. There are no accounts, ads or tracking.`
      },
      {
        heading: 'Features',
        content: `**The custom lock screen does not appear.**
Turn it on in Settings, allow Display over other apps, and check that battery optimization is not stopping Launch in the background.

**Usage charts are empty.**
Allow Usage access from the permissions page of the tour or in Settings.

**Notification badges or lock screen alerts are missing.**
Allow Notification access for Launch in Android settings.

**Files shows nothing.**
Allow All files access, then pull down to refresh.

**Search results feel slow.**
Category views use Android's media index, so a very new file may take a moment to appear.`
      },
      {
        heading: 'Data and privacy',
        content: `**Where is my data stored?**
Only on your phone. Backups are files you export yourself, and you can protect them with a passphrase.

**How do I move to a new phone?**
Export a backup in Settings, then import it from the welcome screen on the new phone.

**Does the AI assistant send my questions anywhere?**
Not the on-device model. Web AI options open the provider's own site, which needs your account.`
      },
      {
        heading: 'Still stuck?',
        content: `Open an issue on GitHub with your phone model and Android version, and what you expected to happen.`
      }
    ]
  },
};
